package app.mcorg.domain.model.resources

/**
 * Which client last set a resource's collected count (MCO-284).
 *
 * The Seam Companion mod's snapshot is authoritative while the mod is running; the web app's manual
 * counters are the fallback when it isn't. Last-write-wins needs both sides to be able to tell
 * which one wrote the value they're reading.
 *
 * Not to be confused with `ResourceGatheringItem.sourceType`, which is the item's *acquisition*
 * type from the graph (MINED, CRAFTED, …).
 */
enum class ProgressSource(val value: String) {
    /** Set through the web app. */
    MANUAL("manual"),

    /** Set through the mod-facing sync endpoint. */
    MOD("mod");

    companion object {
        /**
         * Parse a stored value. Anything unrecognised — including null, which is what a LEFT JOIN
         * yields for an item with no progress row yet — reads as [MANUAL].
         */
        fun fromValue(value: String?): ProgressSource =
            entries.firstOrNull { it.value == value } ?: MANUAL
    }
}
