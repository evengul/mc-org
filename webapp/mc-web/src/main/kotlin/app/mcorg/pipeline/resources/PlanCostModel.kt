package app.mcorg.pipeline.resources

import app.mcorg.config.CacheManager
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.plan.SupplySource
import app.mcorg.engine.plan.UnitCostModel
import java.time.Instant

/**
 * The one [UnitCostModel] this process uses for a given `(graph, supplied)`.
 *
 * Everything that ranks sources goes through here — the planner
 * ([app.mcorg.engine.plan.GatheringPlanner.plan]), the drill's source picker
 * ([app.mcorg.engine.plan.SourceRanking]) and the tag-member ranking behind "recommended"
 * ([TagMemberRanking]). That is the point of it existing rather than each of them constructing
 * its own: two models built from the same inputs agree, but only by luck of them being *given*
 * the same inputs, and MCO-521 is a bug report about exactly that class of drift — the picker and
 * the planner ranking by two different models and disagreeing about what "best" means.
 *
 * ## Why it is cached
 *
 * The `SelectionScorer` this replaced was free to construct and scored one candidate per call.
 * A [UnitCostModel] is a whole-graph relaxation: 1353 items, 6 passes, **~100 ms** on 1.21.4,
 * memoised per instance. Both callers are on read paths that would pay it repeatedly — plans are
 * re-derived on every read, and the drill renders a picker per node — so constructing per call
 * would have made this a performance regression wearing a model change's clothes.
 *
 * ## Why the key has three parts
 *
 * - **version** — different game data, different prices.
 * - **[builtAt]** — the graph's build instant. A re-ingest in the ingestion JVM rebuilds the graph
 *   (MCO-252) and the model must not survive it; keying on the instant retires the old model
 *   without needing an invalidation call that someone has to remember to make.
 * - **supplied** — a farm or a linked project makes an item cost nothing, and that propagates to
 *   everything downstream of it. Two projects in one world with different farms genuinely have
 *   different prices for the same item, so they must not share a model.
 */
object PlanCostModel {

    /**
     * The model for this graph and supply, built once and reused.
     *
     * @param builtAt the graph's build instant, from
     *   [app.mcorg.config.CachedItemSourceGraph.builtAt].
     */
    fun of(
        version: String,
        builtAt: Instant,
        graph: ItemSourceGraph,
        supplied: Map<String, SupplySource> = emptyMap(),
    ): UnitCostModel = CacheManager.unitCostModel.get(key(version, builtAt, supplied.keys)) {
        UnitCostModel(graph, supplied.keys)
    }

    /**
     * Sorted, so two callers that assembled the same supply in a different order share a model
     * rather than relaxing the graph twice for the same answer.
     */
    private fun key(version: String, builtAt: Instant, supplied: Set<String>): String =
        "$version|${builtAt.toEpochMilli()}|${supplied.sorted().joinToString(",")}"
}
