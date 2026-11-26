package app.mcorg.pipeline.resources

/**
 * Where each farm's block goes in a plan's Collect section (MCO-604, design frame 2a).
 *
 * The section used to be one panel of lines ordered by quantity, each tagged with its farm. That
 * ordering put a 455 Bow that takes a skeleton farm 22.8 hours among the small jobs, and the farm
 * chip that said where a line came from is hidden at 375 px. Grouping by farm and ordering by time
 * answers the question a player has when they walk out to their farms: which one do I leave
 * running longest.
 *
 * - [shown]: farms with a time, longest first, then farms with no time (a rate of 0). A farm with
 *   no time cannot be placed by it, so it goes last, and it never folds: it still supplies, and
 *   folding it would hide the "Add a rate" that gives it a time.
 * - [folded]: farms under [FOLD_BELOW_HOURS], when the section has more than [FOLD_WHEN_MORE_THAN]
 *   farms still running. YAMS folds seven farms; Copper Library has five and folds none. A farm
 *   with a line in progress never folds, so a run you are halfway through does not vanish from
 *   under you when it drops under half an hour.
 * - [done]: farms whose every line is logged. They have no time, so they collapse to one line.
 *
 * Order and fold use the time left, not the time the whole run needed (decided 2026-10-09).
 */
data class CollectBlocks(
    val shown: List<FarmRun>,
    val folded: List<FarmRun>,
    val done: List<FarmRun>,
) {
    companion object {
        const val FOLD_BELOW_HOURS: Double = 0.5
        const val FOLD_WHEN_MORE_THAN: Int = 6

        fun of(runs: List<FarmRun>): CollectBlocks {
            val (done, open) = runs.partition { it.isDone }
            val timed = open
                .filter { it.hoursLeft != null }
                .sortedWith(compareByDescending<FarmRun> { it.hoursLeft }.thenBy { it.projectName })
            val untimed = open.filter { it.hoursLeft == null }.sortedBy { it.projectName }

            val folds = open.size > FOLD_WHEN_MORE_THAN
            val (folded, kept) = timed.partition { run ->
                folds && run.hoursLeft!! < FOLD_BELOW_HOURS && !run.isInProgress
            }
            return CollectBlocks(
                shown = kept + untimed,
                folded = folded,
                done = done.sortedBy { it.projectName },
            )
        }
    }
}
