package app.mcorg.pipeline.resources

import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.plan.Activity
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.renewability.Renewability

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
        isRenewable: (String) -> Boolean,
    ): List<FarmScaleDemand> = classify(plan, threshold, isRenewable).filter { it.itemId !in dismissed }

    /** Item ids in [plan] that are farm-scale — for marking rows without re-deriving the rule. */
    fun itemIdsIn(
        plan: GatheringPlan,
        threshold: Int,
        dismissed: Set<String> = emptySet(),
        isRenewable: (String) -> Boolean,
    ): Set<String> =
        of(plan, threshold, dismissed, isRenewable).mapTo(mutableSetOf()) { it.itemId }

    /**
     * The lines [dismissed] is currently suppressing, largest first — what the undo list shows.
     *
     * Only lines this plan actually has: a world dismissal covers every project, and printing
     * "0 Water" against a build that never wanted water would be noise. The undo list names the
     * rest from the dismissal's own stored label.
     */
    fun dismissedIn(
        plan: GatheringPlan,
        threshold: Int,
        dismissed: Set<String>,
        isRenewable: (String) -> Boolean,
    ): List<FarmScaleDemand> = classify(plan, threshold, isRenewable).filter { it.itemId in dismissed }

    /** Every farm-scale line in [plan], dismissed or not, largest first. */
    private fun classify(plan: GatheringPlan, threshold: Int, isRenewable: (String) -> Boolean): List<FarmScaleDemand> {
        val isFarmScale = farmScaleRule(plan, threshold, isRenewable)
        return plan.activityList
            .filter(isFarmScale)
            .map { FarmScaleDemand(itemId = it.item.id, itemName = it.item.name, quantity = it.quantity) }
            .sortedByDescending { it.quantity }
    }

    /**
     * At or above the threshold, not merely past it: a threshold of 1,728 is read as "a shulker
     * box is enough to want a farm", and exactly one shulker box should qualify.
     *
     * Tool-collected materials are excluded however large the number (MCO-467), and so is
     * anything that is not a material at all (the nether portal). See [isToolCollected].
     *
     * **Only what a farm can make** (MCO-565). 13,000 tuff is real demand that no farm will ever
     * meet, so offering one is advice that cannot be taken. But the leaf is not always what the
     * farm makes: iron demand bottoms out in `raw_iron`, which comes off an ore and is not
     * renewable, while the `iron_ingot` it is smelted into drops from every iron golem. So a
     * non-renewable leaf counts the share of its demand that renewable items consume directly
     * in this plan — the farm makes the ingot, and the raw iron line stands for it. Only that
     * share: glass is renewable (a trading hall) and sand is not, so a build with 10,000 concrete
     * powder and a few glass panes must not list 10,100 sand, nor diamond blocks plus one
     * pickaxe all their diamonds. Tuff into tuff bricks counts nothing, because nothing makes the
     * bricks either.
     *
     * The share is what the threshold reads; the line still shows the plan's quantity. The
     * dismissal records that quantity, and the row it badges prints it, so a line showing the
     * share would put two numbers for one item on the page and make every dismissal look like
     * demand had dropped. The line keeps the leaf's name for the same reason: the dismissal, the
     * badge and the row are all keyed by the item the player gathers.
     */
    private fun farmScaleRule(
        plan: GatheringPlan,
        threshold: Int,
        isRenewable: (String) -> Boolean,
    ): (Activity) -> Boolean {
        // What each renewable consumer takes of each input: one execution of the consumer's
        // source consumes quantityPerCraft, and it runs `crafts` times — the quantifier's own sum.
        val renewableDemand = HashMap<String, Long>()
        for (node in plan.nodes.values) {
            if (!isRenewable(node.item.id)) continue
            for (input in node.requires) {
                renewableDemand.merge(input.itemId, node.crafts * input.quantityPerCraft, Long::plus)
            }
        }
        fun farmScaleShare(activity: Activity): Long =
            if (isRenewable(activity.item.id)) activity.quantity
            // Capped: what consumers take can only exceed the leaf if the two sums ever drift.
            else minOf(activity.quantity, renewableDemand[activity.item.id] ?: 0L)

        return { activity ->
            activity.status == PlanNodeStatus.RAW_GATHER &&
                activity.quantity >= threshold &&
                !activity.isToolCollected() &&
                activity.item.id !in Renewability.NOT_MATERIALS &&
                farmScaleShare(activity) >= threshold
        }
    }

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
