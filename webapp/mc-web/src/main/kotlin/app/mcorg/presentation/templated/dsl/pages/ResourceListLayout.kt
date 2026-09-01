package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.engine.plan.ActivityGroup
import app.mcorg.engine.plan.GatheringPlan

/**
 * How the project page's resource list is divided up (MCO-478).
 *
 * Pure and separately tested, because the whole change lives in its arithmetic: a flat list of
 * 555 rows became a 25,000px page, and which rows survive on screen is a judgement the template
 * should not be making inline.
 *
 * Measured against *Storage System YAMS* — 555 rows, 107,914 items — **464 rows require exactly
 * one item.** They are 0.43% of the work and 84% of the page. The list was never mis-sorted
 * (`ORDER BY required DESC, name` was already right); it simply never stopped.
 */
object ResourceListLayout {

    /**
     * A row is "odds and ends" at one required item. Deliberately not a share-of-total
     * threshold: "you need one of these" is a fact about the row that a reader can check,
     * whereas "this is under 0.4% of the build" is a fact about the row's *neighbours* — it
     * would move rows in and out of the fold as unrelated quantities changed.
     */
    const val TRIVIA_MAX_REQUIRED = 1

    /**
     * Below this, folding costs more than it saves — a disclosure hiding four rows is just a
     * thing to click. The guard is what stops this being tuned to one project: a build with a
     * handful of single-item rows keeps them all on screen.
     */
    const val MIN_TRIVIA_TO_FOLD = 12

    /** Rows the plan says nothing about. Kept and named rather than dropped or hidden. */
    const val UNPLANNED_LABEL = "Not in the plan yet"

    /**
     * [group] is null for rows the plan does not cover.
     *
     * Deliberately the enum and not a label: naming an [ActivityGroup] is the template's job,
     * and it already had a `groupLabel` for the other resolution of this same list. A second
     * map here disagreed with it on two of nine values within a day of being written.
     */
    data class Group(
        val group: ActivityGroup?,
        val rows: List<ResourceGatheringItem>,
    ) {
        val items: Long get() = rows.sumOf { it.required.toLong() }
    }

    data class Layout(
        val groups: List<Group>,
        /** The folded tail, in the order it would have rendered. */
        val folded: List<ResourceGatheringItem>,
    ) {
        val foldedItems: Long get() = folded.sumOf { it.required.toLong() }
        val visibleCount: Int get() = groups.sumOf { it.rows.size }
        val totalItems: Long get() = groups.sumOf { it.items } + foldedItems

        /**
         * False when the plan told us nothing — everything landed in one "not in the plan yet"
         * bucket. The template then drops the headings rather than printing one heading over
         * the whole list, which would be a label, not a grouping.
         */
        val isGrouped: Boolean get() = groups.any { it.group != null }
    }

    /**
     * [resources] arrives already ordered (required DESC, name) and that order is preserved
     * inside each group — grouping re-buckets rows, it never re-ranks them.
     */
    fun of(resources: List<ResourceGatheringItem>, plan: GatheringPlan?): Layout {
        val active = resources.filter { it.required > 0 && !it.ignored }
        val groupByItemId: Map<String, ActivityGroup> =
            plan?.activityList?.associate { it.item.id to it.group } ?: emptyMap()

        val candidates = active.filter { isTrivia(it, groupByItemId) }
        // Never fold the list out of existence: a build of nothing but single items is a real
        // build, and hiding all of it behind a disclosure would leave an empty table.
        val fold = candidates.size >= MIN_TRIVIA_TO_FOLD && candidates.size < active.size

        val (folded, visible) =
            if (fold) active.partition { isTrivia(it, groupByItemId) } else emptyList<ResourceGatheringItem>() to active

        return Layout(groups = group(visible, groupByItemId), folded = folded)
    }

    /**
     * Progress is the override: once you have collected any of a row it is work in flight, and
     * burying it would lose that. A row awaiting a decision is never folded either — it is the
     * one kind of row that needs the reader.
     */
    private fun isTrivia(
        item: ResourceGatheringItem,
        groupByItemId: Map<String, ActivityGroup>,
    ): Boolean = item.required <= TRIVIA_MAX_REQUIRED &&
        item.collected <= 0 &&
        groupByItemId[item.itemId] != ActivityGroup.NEEDS_ATTENTION

    private fun group(
        rows: List<ResourceGatheringItem>,
        groupByItemId: Map<String, ActivityGroup>,
    ): List<Group> {
        val byGroup = rows.groupBy { groupByItemId[it.itemId] }
        return buildList {
            // ActivityGroup's declaration order is the preferred session order, so iterating
            // the enum is the ordering — nothing here restates it.
            ActivityGroup.entries.forEach { group ->
                byGroup[group]?.takeIf { it.isNotEmpty() }?.let { add(Group(group, it)) }
            }
            byGroup[null]?.takeIf { it.isNotEmpty() }?.let { add(Group(null, it)) }
        }
    }
}
