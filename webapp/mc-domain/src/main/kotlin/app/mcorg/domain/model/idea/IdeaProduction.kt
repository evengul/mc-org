package app.mcorg.domain.model.idea

/**
 * One way an idea can be run, and what it produces that way.
 *
 * A farm that can be run more than one way produces different things at different rates depending
 * on how: an ice farm at full speed or slowed for lag, a nether fortress farm with a
 * wither-skeleton filter on or off. Modes are flat rather than combinations of axes — the
 * six-mode fortress farm is rare enough that listing six modes beats a model that multiplies
 * dimensions for every farm that has none.
 *
 * Most ideas have exactly one mode. [isImplicit] marks the one created for them without asking,
 * so the UI can keep saying nothing about modes until there are two.
 */
data class IdeaProductionMode(
    val id: Int,
    val name: String,
    val position: Int,
    /** Item id -> items per hour. */
    val rates: Map<String, Int>,
) {
    val isImplicit: Boolean get() = name == DEFAULT_MODE_NAME

    companion object {
        /**
         * The name given to the single mode created for an idea that never mentioned modes.
         * Only ever shown when a second mode exists to contrast it with.
         */
        const val DEFAULT_MODE_NAME = "Default"
    }
}

/**
 * The best rate any of [modes] achieves for [itemId], with the mode that achieves it.
 *
 * "Best" rather than a designated default (decision, 2026-08-16): the question a farm suggestion
 * answers is "can this cover my demand at all", and rejecting a farm because its *default* mode is
 * the slow one would hide a farm that plainly covers the work. The mode is returned alongside so
 * the answer can name its own assumption — "62,000/h in Max speed" — rather than quietly promise
 * a best case.
 */
fun List<IdeaProductionMode>.bestRateFor(itemId: String): Pair<IdeaProductionMode, Int>? =
    mapNotNull { mode -> mode.rates[itemId]?.let { mode to it } }
        .maxByOrNull { (_, rate) -> rate }
