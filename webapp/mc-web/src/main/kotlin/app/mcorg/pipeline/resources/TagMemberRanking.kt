package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.model.SourceNode
import app.mcorg.engine.plan.MemberPrior
import app.mcorg.engine.plan.SourceRanking

/**
 * Ranks the members of an open tag — "Red Sand or Sand", "Charcoal or Coal" — so that a picker
 * and anything that answers on the user's behalf are reading the *same* order.
 *
 * There is exactly one notion of "recommended" in the product and this is it: each member scored
 * by the best of its own sources under the engine's [SourceRanking] (a read-only view over
 * `SelectionScorer`), with [MemberPrior] as the tiebreak for members the scorer genuinely cannot
 * separate — TNT accepts sand or red sand and the graph says nothing about which a player reaches
 * for. Nothing here scores anything itself.
 *
 * This lived inline in `DrillView.nodePickerFragment` until MCO-507 needed the same answer on the
 * server, to apply the whole folded tail of small questions in one action. A second copy would
 * have let the button and the picker disagree about what "recommended" means, which is the one
 * failure that would make the action untrustworthy — it does not need to be right, but it does
 * need to be the same thing the picker marks "best score ★".
 */
object TagMemberRanking {

    /** A tag member paired with its best source and that source's score. */
    data class RankedMember(val member: MinecraftId, val bestSource: SourceNode?, val score: Int)

    /**
     * [members] ordered best-first. A member with no source at all in the graph sorts last
     * ([Int.MIN_VALUE]) rather than being dropped — the picker still has to offer it.
     *
     * @param demand the demand to score against; the score is demand-sensitive through the
     *   recipe-threshold bonus, so callers pass the demand their surface is presenting.
     */
    fun rank(graph: ItemSourceGraph?, members: List<MinecraftId>, demand: Long): List<RankedMember> =
        members
            .map { member ->
                val best = graph?.let { SourceRanking.rankSources(it, member, demand).firstOrNull() }
                RankedMember(member, best?.source, best?.score ?: Int.MIN_VALUE)
            }
            .sortedWith(
                compareByDescending<RankedMember> { it.score }
                    .then(MemberPrior.comparator { it.member })
                    .thenBy { it.member.name }
            )

    /**
     * The member to recommend for this choice, or null when there is nothing to recommend
     * (no graph, or fewer than two members — a set of one is not a question).
     */
    fun recommended(graph: ItemSourceGraph?, members: List<MinecraftId>, demand: Long): MinecraftId? {
        if (graph == null || members.size < 2) return null
        return rank(graph, members, demand).firstOrNull()
            ?.takeIf { it.score != Int.MIN_VALUE }
            ?.member
    }
}
