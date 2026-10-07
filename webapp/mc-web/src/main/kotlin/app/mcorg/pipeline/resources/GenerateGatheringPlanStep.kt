package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.domain.model.resources.ResourceSourceType
import app.mcorg.pipeline.Step
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
import java.time.Instant

/**
 * Input bundle for [GenerateGatheringPlanStep].
 *
 * @param projectId the project whose resource_gathering rows are the planning targets
 *   and whose persisted [PlanOverrides] (source pins and tag-member choices) are loaded.
 * @param worldId the world that owns the project — used to resolve its Minecraft version,
 *   which drives the cached [ItemSourceGraph].
 * @param assumeBuilt projects to plan *as if* they were producing (MCO-572), on top of the world's
 *   DONE farms. Empty — every existing caller — means exactly what it always meant. A non-empty set
 *   is a hypothetical plan: it is returned but never written to `project_demand`, which must only
 *   ever hold the world as it is. The roadmap caches those separately ([ScenarioDemand]).
 */
data class GatheringPlanInput(
    val projectId: Int,
    val worldId: Int,
    val assumeBuilt: Set<Int> = emptySet(),
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
/**
 * A question worth less than this share of the plan's minutes is answered rather than asked
 * (MCO-410). **0.01%**, and the number is measured rather than felt.
 *
 * Measured on the only two real projects that exist, world 3:
 *
 * ```
 *   shulker_boxes             9.65%   <- ask
 *   planks                    6.60%   <- ask
 *   smelts_to_glass           1.81%   <- ask
 *   wooden_slabs              0.16%   <- ask
 *   stone_crafting_materials  0.047%  <- ASK, and this is the constraint
 *   coals                     0.0014% <- assume
 *   logs                      0.0011% <- assume
 *   soul_fire_base_blocks     0.0002% <- assume
 * ```
 *
 * `stone_crafting_materials` is what pins it. By share it is trivial — a twentieth of a percent —
 * but it is among the questions real users have actually answered, so a plausible-looking 1%
 * threshold would have decided it for them. This leaves fifty times the margin.
 *
 * Two honest caveats on that reasoning, both worth knowing before anyone raises the number:
 *
 * - The override rows behind "users answered this" were read from MCO-410's write-up, not from
 *   the database in front of you. Live overrides move; check before leaning on them again.
 * - In world 3 the question is answered anyway, because cobblestone is farm-supplied and a tag
 *   with a supplied member costs *nothing*, which is below every threshold rather than below this
 *   one. That is deliberate and safe for a reason unrelated to this number — see [TagAssumptions]:
 *   an open tag is always a recipe ingredient, so no assumption can change what the build ends up
 *   containing, only which pile is gathered on the way.
 *
 * What is left below the line is exactly the tail MCO-410 named when it was filed —
 * `soul_fire_base_blocks` and `coals` — which is the strongest evidence available that the metric
 * and the number agree with the intent.
 *
 * It is a share rather than a count on purpose: three coal is nothing here and everything in a
 * starter base, and only a share means the same thing in both.
 */
private const val ASSUME_TAG_BELOW_SHARE = 0.0001

/** A derived plan, and what its write-through would store, between [GenerateGatheringPlanStep.derive] and its store. */
internal data class Derivation(val plan: GatheringPlan?, val write: DemandWrite?)

/** What a real (not hypothetical) derivation writes back; [generation] is what it read before its inputs. */
internal data class DemandWrite(
    val projectId: Int,
    val generation: Long,
    val fingerprint: DemandFingerprint,
    val gameDataEpoch: Instant?,
    val plan: GatheringPlan?,
)

object GenerateGatheringPlanStep : Step<GatheringPlanInput, AppFailure, GatheringPlan> {

    private val worldVersionQuery = DatabaseSteps.query<Int, String?>(
        sql = SafeSQL.select("SELECT version FROM world WHERE id = ?"),
        parameterSetter = { ps, worldId -> ps.setInt(1, worldId) },
        resultMapper = { rs -> if (rs.next()) rs.getString("version") else null }
    )

    override suspend fun process(input: GatheringPlanInput): Result<AppFailure, GatheringPlan> {
        val derivation = when (val r = derive(input)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        derivation.write?.let { store(it) }
        return derivation.plan?.let { Result.success(it) } ?: Result.failure(
            AppFailure.customValidationError("targets", "All items are fully collected — nothing left to plan")
        )
    }

    /**
     * The read half: everything up to the plan, and what [store] would write for it. Split from
     * [process] so a test can land an invalidation between the two (MCO-584); nothing else calls
     * it. A null plan is "nothing left to plan".
     */
    internal suspend fun derive(input: GatheringPlanInput): Result<AppFailure, Derivation> {
        // 0. The generation of the project's stored demand, before any input is read: the write
        // lands only if no invalidation has happened since (SaveProjectDemandStep). Hypothetical
        // plans are never written, so never need it. A failed read only loses the write-through,
        // as a failed write does.
        val generation = if (input.assumeBuilt.isEmpty()) {
            GetDemandGenerationStep(input.projectId).process(Unit).getOrNull()
        } else {
            null
        }

        // 1. Resolve world version
        val versionString = when (val r = worldVersionQuery.process(input.worldId)) {
            is Result.Success -> r.value ?: return Result.failure(AppFailure.DatabaseError.NotFound)
            is Result.Failure -> return r
        }

        // 1b. When that version's game data last finished ingesting, stored with the plan. Read
        // from the ledger rather than the TTL cache, which also refreshes the cache so step 2
        // rebuilds a graph that predates it: a cached epoch would let a plan built on the old
        // graph be stored as a plan of the new data. Read before the graph, so a re-ingest landing
        // between the two makes this plan look older than its data (one redundant re-derive),
        // never newer.
        val gameDataEpoch = GetItemSourceGraphForVersionStep.freshEpoch(versionString)

        // 2. Get (or build and cache) the item-source graph for that version. Taken as the cached
        // entry rather than the bare graph because the cost model is keyed by its build instant.
        val cachedGraph = when (val r = GetItemSourceGraphForVersionStep.cached(versionString)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        val graph: ItemSourceGraph = cachedGraph.graph

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
            // Nothing left to plan is a derivation too: the project's stored rows are cleared and
            // it is marked derived, or the roadmap would keep drawing the demand it had before the
            // last item was collected (or the last target ignored).
            val write = generation?.let {
                demandWrite(
                    input.projectId, it, versionString, gameDataEpoch, activeItems,
                    supplied = emptyMap(), overrides = PlanOverrides.NONE, plan = null, woodSpecies = null,
                )
            }
            return Result.success(Derivation(plan = null, write = write))
        }

        // 6. Build supplied map. The rule lives in ProjectSupply so the drill's picker can reach
        // the same answer (MCO-523) — folded from the rows already loaded here rather than
        // re-queried.
        val running = when (val r = GetWorldFarmSuppliesStep.process(
            WorldFarmSuppliesInput(worldId = input.worldId, excludeProjectId = input.projectId)
        )) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        // Appended after the running farms, so an item both make keeps the running farm's label.
        val assumed = if (input.assumeBuilt.isEmpty()) {
            emptyList()
        } else {
            when (val r = GetAssumedFarmSuppliesStep.process(
                AssumedFarmSuppliesInput(projectIds = input.assumeBuilt, excludeProjectId = input.projectId)
            )) {
                is Result.Success -> r.value
                is Result.Failure -> return r
            }
        }
        val farms = running + assumed
        val supplied: Map<String, SupplySource> = ProjectSupply.fold(activeItems, farms)

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
        // The cost model is shared with the drill's source picker through PlanCostModel, so the
        // picker's "best" is the same call the plan ranked with (MCO-521). Building one is a
        // whole-graph relaxation (~100 ms) and plans are re-derived on every read, so it is cached
        // per (graph, supplied) rather than constructed here.
        val plan = GatheringPlanner.plan(
            graph, targets, supplied, overrides, PlanContext(woodSpecies = woodSpecies, assumeTagBelowShare = ASSUME_TAG_BELOW_SHARE),
            costModel = PlanCostModel.of(versionString, cachedGraph.builtAt, graph, supplied),
        )

        // 9. Materialise the demand this plan implies (MCO-316), so the roadmap can match farms
        // against what the build actually consumes without deriving a plan per project. Written
        // here because this is the one place a plan already exists; skipped when nothing that
        // feeds the derivation has changed since the last write.
        // A hypothetical plan never lands here: `project_demand` is the world as it is, and every
        // reader of it — the roadmap's edges first of all — would believe the farms were built.
        val write = generation?.let {
            demandWrite(input.projectId, it, versionString, gameDataEpoch, activeItems, supplied, overrides, plan, woodSpecies)
        }

        return Result.success(Derivation(plan = plan, write = write))
    }

    private fun demandWrite(
        projectId: Int,
        generation: Long,
        worldVersion: String,
        gameDataEpoch: Instant?,
        activeItems: List<ResourceGatheringItem>,
        supplied: Map<String, SupplySource>,
        overrides: PlanOverrides,
        plan: GatheringPlan?,
        woodSpecies: String?,
    ) = DemandWrite(
        projectId = projectId,
        generation = generation,
        fingerprint = DemandFingerprint.of(
            worldVersion = worldVersion,
            targets = activeItems.map {
                Triple(it.itemId, (it.required - it.collected).toLong(), it.sourceType?.name)
            },
            supplied = supplied.mapValues { (_, source) -> source.toString() },
            overrides = overrides.sourceByItem.map { "src:${it.key}" to it.value } +
                overrides.tagMember.map { "tag:${it.key}" to it.value },
            woodSpecies = woodSpecies,
            gameDataEpoch = gameDataEpoch,
        ),
        gameDataEpoch = gameDataEpoch,
        plan = plan,
    )

    /**
     * The write half: write-through of the derived demand. Deliberately best-effort.
     *
     * [project_demand] is a cache of something recomputable, and this runs on a read path — a
     * page that renders a plan should not fail because a cache write did. A failure leaves the
     * previous rows in place with their old fingerprint, so the next derivation retries. So does
     * an invalidation that landed after [derive] started reading: [SaveProjectDemandStep] then
     * writes nothing, and the project stays uncovered until it is derived from the new inputs.
     */
    internal suspend fun store(write: DemandWrite) {
        val stored = GetStoredDemandFingerprintStep(write.projectId).process(Unit)
        if (stored is Result.Success && stored.value == write.fingerprint) return

        val saved = SaveProjectDemandStep(write.projectId, write.fingerprint, write.generation, write.gameDataEpoch)
            .process(write.plan)
        when (saved) {
            // No exception and no row data in the message: a PostgreSQL error appends
            // `DETAIL: Key (col)=(value)`, which is user content. See documentation/logging.md.
            is Result.Failure -> logger.warn("Could not store derived demand for project {}", write.projectId)
            is Result.Success -> if (!saved.value) {
                logger.debug("Demand: project {} was invalidated mid-derivation; not stored", write.projectId)
            }
        }
    }

    private val logger = LoggerFactory.getLogger(GenerateGatheringPlanStep::class.java)
}
