package app.mcorg.pipeline.resources

import app.mcorg.engine.plan.Activity
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNodeStatus

/**
 * One raw material whose demand is large enough to be worth a farm (MCO-401).
 *
 * Not a suggestion of *which* farm — that is MCO-294, and it needs an idea bank this does
 * not. This is only the classification: "this quantity is farm-scale", which is computable
 * from the plan alone and is the input that turns one imported build into a list of
 * candidate prerequisite farm projects.
 */
data class FarmScaleDemand(
    val itemId: String,
    val itemName: String,
    val quantity: Long,
)

/**
 * Classifies plan demand against a world's farm-scale threshold.
 *
 * ## What counts
 *
 * **Raw-gather leaves only.** A crafted intermediate is not farmable — its *inputs* are, and
 * they appear in the plan in their own right. Marking "21,888 Stick" would suggest building a
 * stick farm rather than a tree farm, and double-counts wood that is already listed.
 *
 * **Supplied items are excluded automatically**, without a special case: an item an operational
 * farm already covers resolves to [PlanNodeStatus.SUPPLIED], not [PlanNodeStatus.RAW_GATHER], so
 * it never reaches the threshold test. That is the whole reason this reads plan status rather
 * than raw quantities — telling someone to build a gold farm they already have is exactly the
 * failure MCO-316 was about.
 *
 * ## What this V1 deliberately does not do
 *
 * - **Cost is ignored.** 1,728 diamonds and 1,728 cobblestone are not the same problem, but
 *   weighting by acquisition cost needs a cost model that does not exist yet. Absolute count is
 *   the honest version; the flaw is real and worth knowing rather than hiding behind a formula.
 * - **No family grouping.** 1,000 each of six plank types is a tree farm, yet no single row
 *   crosses the line. Families are what OPEN_TAG rows already represent, and those are not
 *   raw-gather — see below.
 * - **Open tags are invisible here.** An unresolved tag ("#minecraft:planks", 121,774 on the
 *   YAMS import — the single largest line in the plan) is [PlanNodeStatus.OPEN_TAG], not
 *   raw-gather, so it is not classified at all. Resolving the tag turns it into real raw demand
 *   that this then sees. Until MCO-400 makes that wall tractable, the roll-up understates a
 *   plan with many open tags, and says so rather than guessing.
 */
object FarmScaleDemands {

    /** Farm-scale raw demand in [plan], largest first. */
    fun of(plan: GatheringPlan, threshold: Int): List<FarmScaleDemand> =
        plan.activityList
            .filter { it.isFarmScale(threshold) }
            .map { FarmScaleDemand(itemId = it.item.id, itemName = it.item.name, quantity = it.quantity) }
            .sortedByDescending { it.quantity }

    /** Item ids in [plan] that are farm-scale — for marking rows without re-deriving the rule. */
    fun itemIdsIn(plan: GatheringPlan, threshold: Int): Set<String> =
        plan.activityList.filter { it.isFarmScale(threshold) }.mapTo(mutableSetOf()) { it.item.id }

    /**
     * At or above the threshold, not merely past it: a threshold of 1,728 is read as "a shulker
     * box is enough to want a farm", and exactly one shulker box should qualify.
     */
    private fun Activity.isFarmScale(threshold: Int): Boolean =
        status == PlanNodeStatus.RAW_GATHER && quantity >= threshold
}
