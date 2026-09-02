package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.engine.plan.Activity
import app.mcorg.engine.plan.ActivityGroup
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNodeStatus

/**
 * "What do I do right now" (MCO-481).
 *
 * MCO-224 specced this as a lens; it is a widget instead, because the answer is worth having
 * *while* you work rather than somewhere you have to navigate to.
 *
 * Ordering, in the order it was arrived at:
 *
 * 1. **Decisions first**, capped. An unpicked variant makes everything below it provisional.
 * 2. **Then leaves, biggest first.** A RAW_GATHER or SUPPLIED node has no inputs, so it is
 *    always something you can go and do right now — a trip to a farm or out into the world.
 * 3. **Then the rest, biggest first.**
 *
 * The engine's own list order was the obvious candidate for (2) and is wrong: it is topological
 * with ties broken by name, so its head is whatever sorts first alphabetically. Driven against
 * the real plan it suggested "1 Black Terracotta" and "2 Azure Bluet" — technically actionable,
 * useless as an answer to "what now".
 */
object NextUpPick {

    /** Enough alternatives that "something else" is useful, few enough to render inline. */
    const val CANDIDATES = 5

    /**
     * At most this many decisions among the candidates.
     *
     * Without the cap a plan like YAMS — 23 open variant choices — fills every slot with
     * decisions, so "something else" only ever offers another question. The point of the widget
     * is that there is always something you can go and *do*.
     */
    const val MAX_DECISIONS = 2

    /**
     * Ordered candidates, best first. Empty when there is nothing left to do.
     *
     * [progress] is item id -> amount collected, the same map the rows use.
     */
    fun of(plan: GatheringPlan?, progress: Map<String, Int> = emptyMap()): List<Activity> {
        if (plan == null) return emptyList()

        val outstanding = plan.activityList.filter { activity ->
            // A node whose demand is already met is not a move, whatever its status.
            (progress[activity.item.id]?.toLong() ?: 0L) < activity.quantity
        }

        // Decisions first, and not as a matter of taste: an unpicked variant means the plan
        // below it is provisional, so acting on anything downstream risks gathering for a chain
        // that changes. BLOCKED sorts in with them — it is the other kind of "this needs you".
        val (decisions, work) = outstanding.partition { it.group == ActivityGroup.NEEDS_ATTENTION }

        // Within decisions, the one that settles the most material is the one worth answering.
        val rankedDecisions = decisions.sortedWith(
            compareByDescending<Activity> { it.quantity }.thenBy { it.item.name }
        )

        val biggestFirst = compareByDescending<Activity> { it.quantity }.thenBy { it.item.name }

        // A leaf has no unmet inputs, so it is the work you can start without doing anything
        // else first. Everything else is real but may be waiting on something above it.
        val (leaves, downstream) = work
            .filter { it.status != PlanNodeStatus.BLOCKED }
            .partition { it.status == PlanNodeStatus.RAW_GATHER || it.status == PlanNodeStatus.SUPPLIED }

        val actionableWork = leaves.sortedWith(biggestFirst) + downstream.sortedWith(biggestFirst)

        return (rankedDecisions.take(MAX_DECISIONS) + actionableWork).take(CANDIDATES)
    }

    /** A short reason the pick is the pick, shown under it. */
    fun reasonFor(activity: Activity, isFirst: Boolean): String = when {
        activity.group == ActivityGroup.NEEDS_ATTENTION ->
            "The plan below this is provisional until you choose."
        activity.status == PlanNodeStatus.SUPPLIED -> "A farm already makes this — go and empty it."
        activity.status == PlanNodeStatus.RAW_GATHER -> "Nothing has to happen first."
        isFirst -> "The largest thing left."
        else -> "Its ingredients are accounted for."
    }
}
