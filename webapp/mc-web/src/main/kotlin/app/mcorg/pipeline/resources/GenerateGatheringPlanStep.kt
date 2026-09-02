package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.domain.model.resources.ResourceSourceType
import app.mcorg.domain.pipeline.Step
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.GatheringPlanner
import app.mcorg.engine.plan.PlanContext
import app.mcorg.engine.plan.PlanOverrides
import app.mcorg.engine.plan.PlanTarget
import app.mcorg.engine.plan.SupplySource
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.GetItemSourceGraphForVersionStep
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep
import org.slf4j.LoggerFactory

/**
 * Input bundle for [GenerateGatheringPlanStep].
 *
 * @param projectId the project whose resource_gathering rows are the planning targets
 *   and whose persisted [PlanOverrides] (source pins and tag-member choices) are loaded.
 * @param worldId the world that owns the project — used to resolve its Minecraft version,
 *   which drives the cached [ItemSourceGraph].
 */
data class GatheringPlanInput(
    val projectId: Int,
    val worldId: Int,
)

/**
 * Derives a [GatheringPlan] for a project's resource gathering without persisting it.
 *
 * Execution order:
 * 1. Load the world's Minecraft version string.
 * 2. Obtain the cached [ItemSourceGraph] for that version.
 * 3. Load all resource_gathering rows for the project.
 * 4. Exclude rows marked `ignored` (MCO-247) — kept in storage for reversibility, but
 *    excluded from the derivation input so shared intermediates recompute without them.
 * 5. Build [PlanTarget]s: amount = max(0, required − collected); skip fully-collected rows.
 * 6. Build the [SupplySource] map (MCO-296): operational (DONE) projects' productions
 *    supply the whole world as [SupplySource.Farm] terminals — targets and engine-derived
 *    intermediates alike. Explicit row-level choices win over ambient farm supply: a
 *    `manual` source pick opts that item out, and a project link overlays it as a
 *    [SupplySource.LinkedProject] terminal.
 * 7. Load persisted [PlanOverrides] for the project.
 * 8. Run [GatheringPlanner.plan] and return the result.
 *
 * Fails with:
 * - [AppFailure.DatabaseError.NotFound] when the world or its version graph is not found.
 * - [AppFailure.DatabaseError.DatabaseError] on any query failure.
 * - [AppFailure.ValidationError] when there are no positive-amount targets (all items
 *   are already fully collected).
 */
object GenerateGatheringPlanStep : Step<GatheringPlanInput, AppFailure, GatheringPlan> {

    private val worldVersionQuery = DatabaseSteps.query<Int, String?>(
        sql = SafeSQL.select("SELECT version FROM world WHERE id = ?"),
        parameterSetter = { ps, worldId -> ps.setInt(1, worldId) },
        resultMapper = { rs -> if (rs.next()) rs.getString("version") else null }
    )

    override suspend fun process(input: GatheringPlanInput): Result<AppFailure, GatheringPlan> {
        // 1. Resolve world version
        val versionString = when (val r = worldVersionQuery.process(input.worldId)) {
            is Result.Success -> r.value ?: return Result.failure(AppFailure.DatabaseError.NotFound)
            is Result.Failure -> return r
        }

        // 2. Get (or build and cache) the item-source graph for that version
        val graph: ItemSourceGraph = when (val r = GetItemSourceGraphForVersionStep.process(versionString)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }

        // 3. Load all resource_gathering rows for this project
        val items: List<ResourceGatheringItem> =
            when (val r = GetAllResourceGatheringItemsStep.process(input.projectId)) {
                is Result.Success -> r.value
                is Result.Failure -> return r
            }

        // 4. Exclude ignored rows (MCO-247) before deriving targets/supply — an ignored
        // row stays in resource_gathering (reversible) but must not feed the plan, so its
        // share of any shared intermediates recomputes as if it were never a target.
        val activeItems = items.filterNot { it.ignored }

        // 5. Build targets — net of collected; skip fully-collected items
        val targets: List<PlanTarget> = activeItems.mapNotNull { item ->
            val net = (item.required - item.collected).toLong()
            if (net <= 0) null
            else PlanTarget(Item(item.itemId, item.name), net)
        }

        if (targets.isEmpty()) {
            return Result.failure(
                AppFailure.customValidationError(
                    "targets",
                    "All items are fully collected — nothing left to plan"
                )
            )
        }

        // 6. Build supplied map — world farm supply first, explicit choices on top.
        // Operational (DONE) projects' productions supply the whole world (MCO-296): any
        // item they produce terminates as a SUPPLIED leaf wherever it appears in a chain.
        // An explicit 'manual' source pick opts the item out of farm supply; an explicit
        // project link replaces the farm entry via the map union below.
        val farms = when (val r = GetWorldFarmSuppliesStep.process(
            WorldFarmSuppliesInput(worldId = input.worldId, excludeProjectId = input.projectId)
        )) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        val manualItems: Set<String> = activeItems
            .filter { it.sourceType == ResourceSourceType.MANUAL }
            .map { it.itemId }
            .toSet()
        val farmSupplied: Map<String, SupplySource> = farms
            .filter { it.itemId !in manualItems }
            .groupBy { it.itemId }
            .mapValues { (_, producers) -> SupplySource.Farm(producers.first().projectName) }
        val linkedSupplied: Map<String, SupplySource> = activeItems
            .mapNotNull { item ->
                val (solvedId, solvedName) = item.solvedByProject ?: return@mapNotNull null
                item.itemId to SupplySource.LinkedProject(solvedId, solvedName)
            }
            .toMap()
        val supplied: Map<String, SupplySource> = farmSupplied + linkedSupplied

        // 7. Load persisted overrides for this project
        val overrides: PlanOverrides = when (val r = GetPlanOverridesStep.process(input.projectId)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }

        // 8. Run the engine, told which tree this world farms (MCO-409). That one answer settles
        // `#planks`, `#wooden_slabs` and `#logs` — three askings of one question — instead of
        // three separate variant prompts. It defaults recipe *ingredients* only: targets are
        // concrete items, so a build that asked for oak planks still gets oak planks.
        val woodSpecies = when (val r = GetPreferredWoodSpeciesStep.process(input.worldId)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        val plan = GatheringPlanner.plan(
            graph, targets, supplied, overrides, PlanContext(woodSpecies = woodSpecies)
        )

        // 9. Materialise the demand this plan implies (MCO-316), so the roadmap can match farms
        // against what the build actually consumes without deriving a plan per project. Written
        // here because this is the one place a plan already exists; skipped when nothing that
        // feeds the derivation has changed since the last write.
        storeDemand(input.projectId, versionString, activeItems, supplied, overrides, plan, woodSpecies)

        return Result.success(plan)
    }

    /**
     * Write-through of the derived demand. Deliberately best-effort.
     *
     * [project_demand] is a cache of something recomputable, and this runs on a read path — a
     * page that renders a plan should not fail because a cache write did. A failure leaves the
     * previous rows in place with their old fingerprint, so the next derivation retries.
     */
    private suspend fun storeDemand(
        projectId: Int,
        worldVersion: String,
        activeItems: List<ResourceGatheringItem>,
        supplied: Map<String, SupplySource>,
        overrides: PlanOverrides,
        plan: GatheringPlan,
        woodSpecies: String?,
    ) {
        val fingerprint = DemandFingerprint.of(
            worldVersion = worldVersion,
            targets = activeItems.map {
                Triple(it.itemId, (it.required - it.collected).toLong(), it.sourceType?.name)
            },
            supplied = supplied.mapValues { (_, source) -> source.toString() },
            overrides = overrides.sourceByItem.map { "src:${it.key}" to it.value } +
                overrides.tagMember.map { "tag:${it.key}" to it.value },
            woodSpecies = woodSpecies,
        )

        val stored = GetStoredDemandFingerprintStep(projectId).process(Unit)
        if (stored is Result.Success && stored.value == fingerprint) return

        val saved = SaveProjectDemandStep(projectId, fingerprint).process(plan)
        if (saved is Result.Failure) {
            // No exception and no row data in the message: a PostgreSQL error appends
            // `DETAIL: Key (col)=(value)`, which is user content. See documentation/logging.md.
            logger.warn("Could not store derived demand for project {}", projectId)
        }
    }

    private val logger = LoggerFactory.getLogger(GenerateGatheringPlanStep::class.java)
}
