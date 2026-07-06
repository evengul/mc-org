package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.domain.pipeline.Step
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.GatheringPlanner
import app.mcorg.engine.plan.PlanOverrides
import app.mcorg.engine.plan.PlanTarget
import app.mcorg.engine.plan.SupplySource
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.GetItemSourceGraphForVersionStep
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep

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
 * 6. Build the [SupplySource] map: rows linked to another project become
 *    [SupplySource.LinkedProject] terminals.
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

        // 6. Build supplied map: rows linked to another project become supply terminals
        val supplied: Map<String, SupplySource> = activeItems
            .mapNotNull { item ->
                val (solvedId, solvedName) = item.solvedByProject ?: return@mapNotNull null
                item.itemId to SupplySource.LinkedProject(solvedId, solvedName)
            }
            .toMap()

        // 7. Load persisted overrides for this project
        val overrides: PlanOverrides = when (val r = GetPlanOverridesStep.process(input.projectId)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }

        // 8. Run the engine
        return Result.success(GatheringPlanner.plan(graph, targets, supplied, overrides))
    }
}
