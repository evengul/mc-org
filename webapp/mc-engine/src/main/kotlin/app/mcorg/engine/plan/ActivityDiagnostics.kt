package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.model.SourceNode

/**
 * How many *kinds of work* a plan asks for, and how many of those an activity-aware tie-break
 * would remove — MCO-493's step 3, which that issue insists comes before any implementation.
 *
 * **Read-only. Changes no ranking.** Like [ScoreDiagnostics], this computes and returns; the CLI
 * in mc-web prints. It lives here rather than in the CLI because it reads [activityGroup], which
 * is the same mapping [GatheringPlan.activityList] groups by — asking this question from a second
 * copy of that `when` is how the placed-form table came to disagree with itself twice.
 *
 * ## The question, and why it is a measurement rather than a design
 *
 * A cost model minimises total minutes. A player also minimises errands: three activities at 40
 * minutes beats twelve at 38, and nothing in either model can say so. The shipped scorer's
 * `REQUIREMENT_PENALTY` was a crude proxy for it, and the cost model deletes that — correctly,
 * since summing the chain is better arithmetic — which loses a real concern that had been riding
 * along on a constant.
 *
 * The tempting fix needs two guesses: a taxonomy of what counts as one activity, and a weight for
 * what one is worth. Either alone reintroduces exactly what the cost model exists to remove.
 *
 * This measures the version that needs **neither**. Roughly a third of the model's disagreements
 * are exact-cost ties, where it has no opinion by construction and the current answer is
 * "whichever source id sorts first". Preferring a tied route whose work the plan already involves
 * is a *counting* question, and counting carries no judgement. It also cannot make a plan more
 * expensive, because every move is between routes priced identically.
 *
 * MCO-493's own bar: *"if YAMS goes 9 -> 7 on ties alone and a weighted rule would only reach 6,
 * the weight is not worth building."* This prints the left-hand numbers.
 *
 * ## What this deliberately does not model, and which way it errs
 *
 * Groups are counted over each item's **own** chosen source. Moving an item to another group does
 * not re-derive the plan, so it does not account for the new route's *ingredients* dragging the
 * old group back in: `green_wool` can leave HUNT by being crafted from `white_wool` and a dye,
 * but if `white_wool` is still sheared then hunting has not left the plan at all.
 *
 * So every exit price here is a **lower bound**, and every removal an **upper bound** on what is
 * really achievable. The asymmetry is usable rather than fatal: a group this says is expensive to
 * leave really is expensive, and one it says is cheap has to be checked against a re-derived plan
 * before anyone believes it. Doing it exactly means re-running selection under the candidate
 * rule — which is building the feature, and is the thing this measurement exists to price.
 */
object ActivityDiagnostics {

    /**
     * What a scope's picks add up to under one effort table — the cheap half of [report].
     *
     * [report] answers "could an activity-aware tie-break remove an errand", and pays a
     * `rankedFeasible` plus a greedy pass per scope to do it. A calibration sweep asks something
     * much smaller of the same picks, but asks it a couple of hundred times — once per swept
     * value per group. So it gets this: one [UnitCostModel.tiedBest] per item, which is what
     * choosing a source costs anyway.
     *
     * Everything here is measured against the model's **own** answers. Nothing is compared to a
     * second model, which is the whole point (MCO-520) — these numbers still mean something once
     * `SelectionScorer` is gone.
     */
    data class Stability(
        /** The cheapest source per item, by the same tie-break [UnitCostModel.best] uses. */
        val picks: Map<String, SourceNode>,
        /**
         * Items where more than one source ties for cheapest.
         *
         * Not a defect and not a disagreement: it is the model saying it has no opinion, and the
         * answer resting on the declared tie-break instead. A row where this is large is a row
         * where the effort value is deciding less than it appears to.
         */
        val ties: Int,
        /** The distinct kinds of work these picks add up to — the plan-level number. */
        val kinds: Set<ActivityGroup>,
        /** Items in the scope that nothing produces at a finite cost. */
        val unpriced: Int,
        /**
         * Summed unit cost over the priced items.
         *
         * Comparable between two tables only while [unpriced] is equal — an item entering or
         * leaving the priced set moves this by its whole cost, which is not the same thing as
         * the plan getting cheaper.
         */
        val totalMinutes: Double,
    )

    /**
     * @param items the scope to measure — a project's item set, or every produced item.
     */
    fun stability(model: UnitCostModel, items: List<MinecraftId>): Stability {
        val picks = LinkedHashMap<String, SourceNode>()
        val kinds = LinkedHashSet<ActivityGroup>()
        var ties = 0
        var unpriced = 0
        var total = 0.0

        for (item in items) {
            val tied = model.tiedBest(item)
            val best = tied.firstOrNull()
            if (best == null) {
                unpriced++
                continue
            }
            picks[item.id] = best
            kinds += best.sourceType.activityGroup()
            if (tied.size > 1) ties++
            // tiedBest already dropped anything at UNREACHABLE, so this is finite by construction.
            total += model.cost[item.id] ?: 0.0
        }

        return Stability(picks, ties, kinds, unpriced, total)
    }

    /** One activity group's standing in a scope: who is in it, and what leaving would cost. */
    data class GroupReport(
        val group: ActivityGroup,
        val items: List<String>,
        /** Items with an equal-cost alternative in some *other* group. */
        val escapable: List<String>,
        /**
         * Minutes per unit it would cost to move every item out of this group, summed — or null
         * when some item has no route out at any price, which makes the group unavoidable.
         *
         * This is the number MCO-493's step 4 needs. A dominance rule ("do not pay more than X
         * minutes to avoid a new kind of work") is only worth building if some group's exit price
         * is small, and only arguable at all because the cost model is denominated in minutes:
         * *"is avoiding a trip to the Nether worth ten minutes of gathering?"* is a question with
         * an answer someone can call wrong.
         */
        val exitCost: Double?,
        /** The dearest single item to move, which is what actually sets the price of the group. */
        val dearestEscape: Pair<String, Double>?,
    ) {
        /**
         * Every member has a tied alternative in *some* other group.
         *
         * Necessary for the group to go, and **not sufficient**: the destination has to be a
         * group the plan still needs, or emptying this one just swaps one errand for another and
         * the count is unchanged. [ScopeReport.removed] applies that second condition; this
         * property is the per-group half, and the two deliberately differ.
         */
        val removable: Boolean get() = items.isNotEmpty() && escapable.size == items.size
    }

    data class ScopeReport(
        val label: String,
        val itemCount: Int,
        /** Items where the model returned more than one cheapest source. */
        val ties: Int,
        /** Ties whose tied sources are not all the same kind of work — the only ones that matter. */
        val tiesAcrossGroups: Int,
        val groups: List<GroupReport>,
        /** Groups the greedy pass emptied, cheapest-to-empty first. */
        val removed: List<ActivityGroup>,
    ) {
        val before: Int get() = groups.size
        val after: Int get() = groups.size - removed.size
    }

    /**
     * @param items the scope to measure — a project's item set, or every produced item.
     *
     * The greedy pass repeatedly empties the *smallest* group all of whose items have an
     * equal-cost home elsewhere. Greedy is deliberate: it answers "does this lever move
     * anything", and it cannot overstate the answer, since a smarter rule could only do better.
     * A tie between two chest sources is still a tie but cannot remove an errand, which is why
     * [tiesAcrossGroups] is counted apart from [ties].
     */
    fun report(
        graph: ItemSourceGraph,
        model: UnitCostModel,
        items: List<MinecraftId>,
        label: String,
    ): ScopeReport {
        val pickedGroup = HashMap<String, ActivityGroup>()
        val reachable = HashMap<String, Set<ActivityGroup>>()
        var ties = 0
        var tiesAcrossGroups = 0

        for (item in items) {
            val candidates = model.tiedBest(item)
            if (candidates.isEmpty()) continue
            val groups = candidates.mapTo(LinkedHashSet()) { it.sourceType.activityGroup() }
            pickedGroup[item.id] = candidates.first().sourceType.activityGroup()
            reachable[item.id] = groups
            if (candidates.size > 1) {
                ties++
                if (groups.size > 1) tiesAcrossGroups++
            }
        }

        val byGroup = pickedGroup.entries
            .groupBy({ it.value }, { it.key })
            .toSortedMap(compareBy { it.ordinal })

        // What it would cost to leave — the step-4 half. Computed per item as the cheapest route
        // in any *other* group, minus what the item costs today.
        val byId = items.associateBy { it.id }
        fun escapePrice(id: String, group: ActivityGroup): Double? {
            val item = byId[id] ?: return null
            val ranked = model.rankedFeasible(item)
            val here = ranked.firstOrNull()?.second ?: return null
            val out = ranked.firstOrNull { it.first.sourceType.activityGroup() != group } ?: return null
            return (out.second - here).coerceAtLeast(0.0)
        }

        val groups = byGroup.map { (group, members) ->
            val prices = members.associateWith { escapePrice(it, group) }
            GroupReport(
                group = group,
                items = members.sorted(),
                escapable = members.filter { id ->
                    reachable[id].orEmpty().any { it != group }
                }.sorted(),
                exitCost = if (prices.values.any { it == null }) null else prices.values.filterNotNull().sum(),
                dearestEscape = prices.entries
                    .filter { it.value != null }
                    .maxByOrNull { it.value!! }
                    ?.let { it.key to it.value!! },
            )
        }

        // Greedy emptying. `live` tracks membership as items move, because emptying one group
        // can leave another one's items with nowhere equal-cost left to go.
        val live = byGroup.mapValues { it.value.toMutableSet() }.toMutableMap()
        val removed = mutableListOf<ActivityGroup>()
        while (true) {
            val next = live.entries
                .filter { (group, members) ->
                    members.isNotEmpty() && members.all { id ->
                        reachable[id].orEmpty().any { it != group && it in live.keys }
                    }
                }
                .minByOrNull { it.value.size } ?: break

            next.value.forEach { id ->
                val home = reachable.getValue(id).first { it != next.key && it in live.keys }
                live[home]?.add(id)
            }
            live.remove(next.key)
            removed += next.key
        }

        return ScopeReport(
            label = label,
            itemCount = items.size,
            ties = ties,
            tiesAcrossGroups = tiesAcrossGroups,
            groups = groups,
            removed = removed,
        )
    }
}
