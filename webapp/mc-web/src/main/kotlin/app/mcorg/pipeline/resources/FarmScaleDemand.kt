package app.mcorg.pipeline.resources

import app.mcorg.domain.model.resources.ResourceSource
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

    /**
     * Farm-scale raw demand in [plan], largest first.
     *
     * [dismissed] is what this world has decided against (MCO-407) — those items are not
     * classified at all rather than classified and hidden, so the same call answers the roll-up,
     * the row badge and the section's own count with one rule. A dismissal is independent of
     * [threshold] on purpose: it exists precisely because the threshold could not express it,
     * and it must survive the threshold being changed or it is only a slower way of raising it.
     */
    fun of(
        plan: GatheringPlan,
        threshold: Int,
        dismissed: Set<String> = emptySet(),
    ): List<FarmScaleDemand> =
        plan.activityList
            .filter { it.isFarmScale(threshold) && it.item.id !in dismissed }
            .map { FarmScaleDemand(itemId = it.item.id, itemName = it.item.name, quantity = it.quantity) }
            .sortedByDescending { it.quantity }

    /** Item ids in [plan] that are farm-scale — for marking rows without re-deriving the rule. */
    fun itemIdsIn(plan: GatheringPlan, threshold: Int, dismissed: Set<String> = emptySet()): Set<String> =
        of(plan, threshold, dismissed).mapTo(mutableSetOf()) { it.itemId }

    /**
     * The lines [dismissed] is currently suppressing, largest first — what the undo list shows.
     *
     * Only lines this plan actually has: a world dismissal covers every project, and printing
     * "0 Water" against a build that never wanted water would be noise. The undo list names the
     * rest from the dismissal's own stored label.
     */
    fun dismissedIn(plan: GatheringPlan, threshold: Int, dismissed: Set<String>): List<FarmScaleDemand> =
        plan.activityList
            .filter { it.isFarmScale(threshold) && it.item.id in dismissed }
            .map { FarmScaleDemand(itemId = it.item.id, itemName = it.item.name, quantity = it.quantity) }
            .sortedByDescending { it.quantity }

    /**
     * At or above the threshold, not merely past it: a threshold of 1,728 is read as "a shulker
     * box is enough to want a farm", and exactly one shulker box should qualify.
     *
     * Tool-collected materials are excluded however large the number (MCO-467). See
     * [isToolCollected].
     */
    private fun Activity.isFarmScale(threshold: Int): Boolean =
        status == PlanNodeStatus.RAW_GATHER && quantity >= threshold && !isToolCollected()

    /**
     * Filled from the world with a tool, rather than gathered — water, lava, and the three
     * filled buckets ([ResourceSource.SourceType.MechanicTypes.COLLECT], see `SyntheticSources`).
     *
     * These are unbounded at the source. You do not need 2,413 water; you need *a bucket*, and
     * then you place it 2,413 times. No farm can produce them and none ever will, so offering
     * one is advice that cannot be taken — and "2,413 Water" sat at the top of the YAMS plan's
     * "Worth a farm" list precisely because the quantity is real while the scarcity is not.
     *
     * **COLLECT specifically, not "a leaf with no inputs".** The wider rule would catch
     * `synthetic/wither.json`, which also requires nothing and produces a nether star — and a
     * wither star farm is a real thing somebody builds. Ice is the other near-miss: it is
     * `minecraft:block` (you break it), it is genuinely farmable, and two ice farm designs sit
     * in the bank. The line is *how* the item leaves the world, and COLLECT draws it exactly.
     *
     * A null source keeps the old answer rather than guessing — an activity with no selected
     * source is not something this rule has an opinion about.
     */
    private fun Activity.isToolCollected(): Boolean =
        source?.sourceType == ResourceSource.SourceType.MechanicTypes.COLLECT
}
