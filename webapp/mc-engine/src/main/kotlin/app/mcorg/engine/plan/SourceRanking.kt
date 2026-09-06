package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.model.SourceNode

/**
 * One candidate source for an item, ranked against its siblings.
 *
 * @param source the candidate.
 * @param cost minutes to acquire one unit through this source — **lower is better**, and
 *   [UnitCostModel.UNREACHABLE] for a candidate the model cannot price. It was a
 *   higher-is-better `Int` score until MCO-521; a cost inverts every comparison, so anything
 *   reading this must sort ascending and must not treat a large number as a good one.
 * @param rank position in the ranking, 0 = best. Unchanged in meaning, and the safest thing
 *   for a UI to read — it says nothing about which direction the underlying number runs.
 */
data class RankedSource(
    val source: SourceNode,
    val cost: Double,
    val rank: Int,
) {
    /** True when the model has no finite price for this source — it is offered, but last. */
    val unpriceable: Boolean get() = cost >= UnitCostModel.UNREACHABLE
}

/**
 * Read-only ranking of an item's candidate sources, for UIs that let a user re-pin a source
 * (the drill picker). It computes nothing of its own: the order is [UnitCostModel.ranked],
 * which is the same call [PlanSelector] ranks with. It exists only because the model is
 * awkward to build correctly from outside the engine — one instance per `(graph, supplied)`
 * — and because the picker should not have to know that.
 *
 * **The picker and the planner must move together.** This used to be described as "a thin view
 * over the *existing* `SelectionScorer` and the exact ordering `PlanSelector` uses internally",
 * and that was the whole point: swapping the model underneath one and not the other splits them
 * apart silently, and the picker's "best" stops being the plan's choice. Sharing [ranked] rather
 * than duplicating a comparator is what makes that structural instead of a promise (MCO-521).
 *
 * There is no `demand` parameter. There was one, because the scorer's recipe-threshold bonus was
 * demand-sensitive and the drill passed `TargetTree.quantityIfAlone`. The cost model has one
 * answer at every demand — measured, not assumed — so a demand argument here would be a
 * parameter that changes nothing, which is worse than none at all. See MCO-522.
 *
 * [supplied] defaults to "nothing supplied", which yields the intrinsic ranking of a source;
 * pass the project's real supplied map — and, better, the [costModel] the plan was built with —
 * to match the planner's own choice exactly.
 */
object SourceRanking {

    /**
     * [item]'s candidate sources, best (cheapest) first.
     *
     * **Every** candidate is returned, including ones with no finite cost, which sort last and
     * report [RankedSource.unpriceable]. The picker has to offer what it cannot price: a user is
     * allowed to pin something the model has no number for, and an option that quietly vanishes
     * is worse than one listed at the bottom.
     *
     * @param costModel reuse the instance the plan was built with. Building one is a whole-graph
     *   relaxation (~100 ms on 1.21.4) and the drill renders a picker per node, so leaving this
     *   null in a render loop is the performance regression this parameter exists to prevent.
     */
    fun rankSources(
        graph: ItemSourceGraph,
        item: MinecraftId,
        supplied: Map<String, SupplySource> = emptyMap(),
        costModel: UnitCostModel? = null,
    ): List<RankedSource> {
        if (graph.getSourcesForItem(item).isEmpty()) return emptyList()
        val model = costModel ?: UnitCostModel(graph, supplied.keys)
        return model.ranked(item)
            .mapIndexed { index, (source, cost) -> RankedSource(source, cost, index) }
    }
}
