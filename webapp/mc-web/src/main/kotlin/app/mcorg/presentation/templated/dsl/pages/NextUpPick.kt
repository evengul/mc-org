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
 * Ordering:
 *
 * 1. **Leaves, biggest first.** A RAW_GATHER or SUPPLIED node has no inputs, so it is always
 *    something you can go and do right now — a trip to a farm or out into the world.
 * 2. **Then the rest, biggest first.**
 *
 * The engine's own list order was the obvious candidate for (1) and is wrong: it is topological
 * with ties broken by name, so its head is whatever sorts first alphabetically. Driven against
 * the real plan it suggested "1 Black Terracotta" and "2 Azure Bluet" — technically actionable,
 * useless as an answer to "what now".
 *
 * ## Decisions are not moves (MCO-504)
 *
 * This used to lead with up to two unanswered variant questions, on the reasoning that an
 * unpicked variant makes everything below it provisional. The reasoning is right and the
 * conclusion was wrong: it made the plan ask the same question in two places at once — here and
 * in "Needs attention", which lists every question ranked by the material it settles.
 *
 * Even's framing is the one that resolves it: *"what's next is mostly relevant AFTER the
 * questions have been answered. When those questions are there, they are the most important
 * thing."* So the two are sequential rather than parallel. While a question is open the page
 * shows the questions and nothing else claims to know what is next; the widget is suppressed by
 * its caller (see `gatheringPlannerContent`), which is also why nothing here needs to render a
 * picker any more.
 *
 * That withdraws MCO-482's inline picker, and keeps what MCO-482 was actually for: a decision is
 * answerable where it is named. It is now named in exactly one place.
 */
object NextUpPick {

    /** Enough alternatives that "something else" is useful, few enough to render inline. */
    const val CANDIDATES = 5

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

        // Only things you can actually go and do. NEEDS_ATTENTION covers both kinds of "this
        // needs you" — an unanswered variant question and a BLOCKED node — and neither is a
        // move: one is a decision, which the questions section owns, and the other has no
        // source at any price. The BLOCKED filter below is kept even though the group check
        // already covers today's data, because "blocked" is a status and could appear under
        // another group without this being revisited.
        val work = outstanding.filter { it.group != ActivityGroup.NEEDS_ATTENTION }

        val biggestFirst = compareByDescending<Activity> { it.quantity }.thenBy { it.item.name }

        // A leaf has no unmet inputs, so it is the work you can start without doing anything
        // else first. Everything else is real but may be waiting on something above it.
        val (leaves, downstream) = work
            .filter { it.status != PlanNodeStatus.BLOCKED }
            .partition { it.status == PlanNodeStatus.RAW_GATHER || it.status == PlanNodeStatus.SUPPLIED }

        return (leaves.sortedWith(biggestFirst) + downstream.sortedWith(biggestFirst)).take(CANDIDATES)
    }

    /** A short reason the pick is the pick, shown under it. */
    fun reasonFor(activity: Activity, isFirst: Boolean): String = when {
        activity.status == PlanNodeStatus.SUPPLIED -> "A farm already makes this — go and empty it."
        activity.status == PlanNodeStatus.RAW_GATHER -> "Nothing has to happen first."
        isFirst -> "The largest thing left."
        else -> "Its ingredients are accounted for."
    }
}
