package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.engine.plan.Activity

/**
 * Which rows of a work section stay on screen, and which fold away (MCO-480).
 *
 * Generalises what `needsAttentionList` already did for variant choices — rank by consequence,
 * show enough to cover most of it, fold the rest — and applies it to the sections that actually
 * dominate the page. Against *Storage System YAMS* the Craft section is 440 rows and 32,252px,
 * two thirds of the whole "How to make it" resolution; **seventeen of those rows carry 90% of
 * its 255,678 items**, and 260 of them need a single item.
 *
 * **Selection is by quantity; order is never touched.** `QUANTITY_SORTED_GROUPS` deliberately
 * excludes Smelt and Craft, which keep the engine's topological order so an ingredient always
 * precedes the thing that consumes it. Re-sorting those by size would read as a work list you
 * cannot follow, so this picks *which* rows survive and then puts them back in the order they
 * arrived in.
 */
object ActivitySectionLayout {

    /** Show rows until they cover this share of the section's material. */
    const val COVERAGE = 0.9

    /**
     * Sections shorter than this are left whole. Smelt (18 rows) and Hunt (17) are ~1,400px and
     * already scannable; folding them would be motion without benefit, and Smelt's top row alone
     * is 94% of its material so coverage would otherwise collapse it to one line.
     */
    const val MIN_ROWS_TO_FOLD = 24

    /** Never show fewer than this, however skewed the distribution. */
    const val MIN_LEAD = 5

    /** Never lead with more than this, however flat it is. */
    const val MAX_LEAD = 25

    /** A remainder of one or two is not worth hiding behind a toggle. */
    const val MIN_FOLDED = 3

    data class Split(
        val lead: List<Activity>,
        val folded: List<Activity>,
    ) {
        val foldedItems: Long get() = folded.sumOf { it.quantity }
        val leadShareOfItems: Int
            get() {
                val total = lead.sumOf { it.quantity } + foldedItems
                if (total <= 0L) return 100
                return ((lead.sumOf { it.quantity } * 100.0) / total).toInt()
            }
    }

    /**
     * [ordered] is the section's rows in the order they should render — quantity-desc for the
     * independent groups, topological for Smelt and Craft. Both output lists preserve it.
     */
    fun of(ordered: List<Activity>): Split {
        if (ordered.size < MIN_ROWS_TO_FOLD) return Split(ordered, emptyList())

        val leadCount = leadCountFor(ordered)
        if (ordered.size - leadCount < MIN_FOLDED) return Split(ordered, emptyList())

        // Identity, not index: the same item can only appear once per section, and matching on
        // it lets the split be computed on a sorted copy and applied to the original order.
        val keep = ordered
            .sortedWith(compareByDescending<Activity> { it.quantity }.thenBy { it.item.name })
            .take(leadCount)
            .mapTo(mutableSetOf()) { it.item.id }

        return Split(
            lead = ordered.filter { it.item.id in keep },
            folded = ordered.filterNot { it.item.id in keep },
        )
    }

    /**
     * How many rows it takes to cover [COVERAGE] of the section's material, bounded both ways.
     *
     * Coverage alone breaks at both extremes: a flat section would expand nearly everything
     * (which is the wall again), and a section with one enormous row would collapse to a single
     * line that reads as a bug.
     */
    private fun leadCountFor(ordered: List<Activity>): Int {
        val total = ordered.sumOf { it.quantity }
        if (total <= 0L) return MIN_LEAD.coerceAtMost(ordered.size)

        var covered = 0L
        var count = 0
        for (activity in ordered.sortedByDescending { it.quantity }) {
            covered += activity.quantity
            count++
            if (count >= MAX_LEAD) break
            if (covered.toDouble() / total >= COVERAGE) break
        }
        return count.coerceIn(MIN_LEAD, MAX_LEAD).coerceAtMost(ordered.size)
    }
}
