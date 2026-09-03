package app.mcorg.engine.plan

/**
 * Diagnostics-only overrides for the [SelectionScorer] factors that **no test pins**.
 *
 * ## Why this exists
 *
 * Mutation-testing every scoring constant against all mc-engine tests (MCO-490) found four
 * behaviours that can be deleted or wrecked with the suite staying green. Nothing records what
 * they were meant to do, so nothing can say whether removing one loses knowledge or only loses
 * tuning — and telling those two apart is the whole job before [SelectionScorer] is replaced
 * by [UnitCostModel].
 *
 * The way to find out is not to write a test pinning each one: most of them are behaviours the
 * cost model deliberately *replaces with arithmetic*, so pinning them would be writing tests to
 * delete. It is to turn each one off against the **real** graph, see which items actually move,
 * and ask what the cost model says about those same items.
 * Where it reaches the shipped answer by arithmetic, the constant was tuning and can go.
 * Where it does not, the constant was carrying a fact about the game — the way
 * `hasConstructiveSibling` turned out to be — and that fact has to be ported, not deleted.
 *
 * Three of the original four are still switchable here. The fourth, a ceiling on the low-yield
 * penalty, is gone: this differential showed it decided nothing the cost model wanted kept, so
 * it was deleted rather than left as a knob.
 *
 * ## What it is not
 *
 * Not a feature flag, and not a tuning surface. [NONE] is the shipped behaviour and is the
 * default everywhere, so production reads exactly the constants it read before. Nothing
 * outside [ScoreDiagnostics] and the `cost-diagnostics` CLI should ever construct a
 * non-[NONE] value; if a mutation ever looks worth shipping, it belongs in the scorer as a
 * real change with a real test, not here.
 *
 * @param tradeEfficiencyGuard when false, trades earn the efficiency bonus like any other
 *   source. Shipped behaviour excludes them: a trade's output per input is an emerald
 *   exchange rate, not saved effort, and a wandering trader's 8-sand-for-1-emerald would
 *   otherwise earn `(8 - 1) * 20 = +140` on a base of 70.
 * @param mineableThresholdGuard when false, the bulk recipe-threshold bonus is granted even
 *   to an item you could simply mine. Shipped behaviour withholds it, so a recipe that only
 *   converts one raw block into another cannot leapfrog the raw gather at bulk demand.
 * @param lowYieldWeight slope of the sub-one-yield penalty. The ceiling that used to sit
 *   beside it was deleted on this differential's own evidence — see
 *   [SelectionScorer.lowYieldPenalty].
 */
data class ScorerMutation(
    val tradeEfficiencyGuard: Boolean = true,
    val mineableThresholdGuard: Boolean = true,
    val lowYieldWeight: Int = DEFAULT_LOW_YIELD_WEIGHT,
) {
    companion object {
        /** The shipped constant. Kept here so the scorer and the mutations cannot drift apart. */
        const val DEFAULT_LOW_YIELD_WEIGHT = 20

        /** Shipped behaviour, byte for byte. The default for every production call. */
        val NONE = ScorerMutation()
    }
}

/**
 * The unpinned behaviours, each as one switch a differential can flip.
 *
 * A factor's [mutate] returns the scorer *without* that behaviour, so the items whose
 * selection changes between [ScorerMutation.NONE] and it are exactly the items the
 * behaviour decides. An empty list means the behaviour is inert on this graph — which is
 * itself the answer, and the reason the count matters more than any single item.
 */
enum class ScorerFactor(val label: String, val describe: String) {
    TRADE_EFFICIENCY_GUARD(
        "trades excluded from the efficiency bonus",
        "a trade's output per emerald is an exchange rate, not saved effort",
    ) {
        override fun mutate(base: ScorerMutation) = base.copy(tradeEfficiencyGuard = false)
    },
    MINEABLE_THRESHOLD_GUARD(
        "bulk recipe bonus withheld for a mineable item",
        "converting one raw block into another saves no effort over mining more",
    ) {
        override fun mutate(base: ScorerMutation) = base.copy(mineableThresholdGuard = false)
    },
    LOW_YIELD_WEIGHT(
        "low-yield penalty weight",
        "a 0.33-per-kill drop costs three kills, so cheap recipes win even at small demand",
    ) {
        override fun mutate(base: ScorerMutation) = base.copy(lowYieldWeight = 0)
    };

    /** The mutation that removes this behaviour, leaving the other three as they are. */
    abstract fun mutate(base: ScorerMutation): ScorerMutation
}
