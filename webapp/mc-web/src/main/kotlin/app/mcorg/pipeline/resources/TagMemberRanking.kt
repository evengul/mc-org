package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.model.SourceNode
import app.mcorg.engine.plan.MemberPrior
import app.mcorg.engine.plan.SourceRanking
import app.mcorg.engine.plan.SupplySource
import app.mcorg.engine.plan.UnitCostModel

/**
 * Ranks the members of an open tag — "Red Sand or Sand", "Charcoal or Coal" — so that a picker
 * and anything that answers on the user's behalf are reading the *same* order.
 *
 * There is exactly one notion of "recommended" in the product and this is it: each member scored
 * by the best of its own sources under the engine's [SourceRanking] (a read-only view over
 * [UnitCostModel], the same model the planner picks with), with [MemberPrior] as the tiebreak for
 * members the model genuinely cannot separate — TNT accepts sand or red sand and the graph says
 * nothing about which a player reaches for. Nothing here scores anything itself.
 *
 * This lived inline in `DrillView.nodePickerFragment` until MCO-507 needed the same answer on the
 * server, to apply the whole folded tail of small questions in one action. A second copy would
 * have let the button and the picker disagree about what "recommended" means, which is the one
 * failure that would make the action untrustworthy — it does not need to be right, but it does
 * need to be the same thing the picker marks as best.
 *
 * Ordering **inverted** with MCO-521: members are ranked by cost in minutes, ascending, where
 * they used to be ranked by a higher-is-better `Int` score. The sentinel inverted with it — a
 * member with no source sorted last at `Int.MIN_VALUE` and now sorts last at
 * [UnitCostModel.UNREACHABLE], which is the *largest* value rather than the smallest. Getting
 * that backwards would have put every unobtainable member at the top of the picker.
 */
object TagMemberRanking {

    /**
     * A tag member paired with its best source and that source's cost in minutes.
     *
     * [cost] is **lower is better**, and [UnitCostModel.UNREACHABLE] for a member with no source
     * at all, or none the model can price.
     */
    data class RankedMember(val member: MinecraftId, val bestSource: SourceNode?, val cost: Double) {
        /** True when there is nothing here to recommend — offered, but last. */
        val unpriceable: Boolean get() = cost >= UnitCostModel.UNREACHABLE
    }

    /**
     * [members] ordered best-first — cheapest first. A member with no source at all in the graph
     * sorts last ([UnitCostModel.UNREACHABLE]) rather than being dropped: the picker still has to
     * offer it.
     *
     * @param costModel the model the plan was built with. Building one is a whole-graph relaxation
     *   (~100 ms), and this is called per picker render, so pass the cached instance.
     */
    fun rank(
        graph: ItemSourceGraph?,
        members: List<MinecraftId>,
        supplied: Map<String, SupplySource> = emptyMap(),
        costModel: UnitCostModel? = null,
    ): List<RankedMember> =
        members
            .map { member ->
                val best = graph?.let {
                    SourceRanking.rankSources(it, member, supplied, costModel).firstOrNull()
                }
                RankedMember(member, best?.source, best?.cost ?: UnitCostModel.UNREACHABLE)
            }
            .sortedWith(
                compareBy<RankedMember> { it.cost }
                    .then(MemberPrior.comparator { it.member })
                    .thenBy { it.member.name }
            )

    /**
     * The member to recommend for this choice, or null when there is nothing to recommend
     * (no graph, or fewer than two members — a set of one is not a question).
     */
    fun recommended(
        graph: ItemSourceGraph?,
        members: List<MinecraftId>,
        supplied: Map<String, SupplySource> = emptyMap(),
        costModel: UnitCostModel? = null,
    ): MinecraftId? {
        if (graph == null || members.size < 2) return null
        return rank(graph, members, supplied, costModel).firstOrNull()
            ?.takeIf { !it.unpriceable }
            ?.member
    }
}
