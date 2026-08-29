package app.mcorg.domain.model.idea

/**
 * Whether a mode is chosen when you *build* a design or while you *run* it (MCO-463).
 *
 * The discriminator is not switchability, it is **what the mode changes**. That distinction is the
 * whole reason this type exists, so it is worth stating plainly rather than leaving to the two
 * names:
 *
 * | | changes | fixed at |
 * |---|---|---|
 * | [RUNTIME] | what the farm supplies | never — flip it whenever you like |
 * | [BUILD_TIME] | what it supplies **and what it costs to build** | import; changing it means rebuilding |
 *
 * A nether fortress farm's wither-skeleton filter is [RUNTIME]: flip the lever and nothing about
 * the build changes. A cobblestone farm's *single module / 4 modules* is [BUILD_TIME]: the 4-module
 * variant costs roughly four times the materials, and it is a different schematic.
 */
enum class IdeaModeKind {
    /** Chosen once, when the thing is built. Carries its own [IdeaProductionMode.requirements]. */
    BUILD_TIME,

    /**
     * Switchable on the built farm. Never carries requirements — a runtime mode by definition does
     * not change what the build cost. These are the modes MCO-413 follows to the project.
     */
    RUNTIME,
    ;

    companion object {
        /**
         * What an unrecognised or absent value means.
         *
         * [RUNTIME], because every mode entered before MCO-463 was answered under a form that only
         * ever described ways of *running* a farm. Reading those as build-time would attribute a
         * choice to their authors that they were never offered. Matches the column default.
         */
        val Default = RUNTIME

        fun fromOrDefault(value: String?): IdeaModeKind =
            entries.firstOrNull { it.name == value } ?: Default
    }
}

/**
 * One way an idea can be run or built, and what that costs and produces.
 *
 * A farm that can be run more than one way produces different things at different rates depending
 * on how: an ice farm at full speed or slowed for lag, a nether fortress farm with a
 * wither-skeleton filter on or off. Modes are flat rather than combinations of axes — the
 * six-mode fortress farm is rare enough that listing six modes beats a model that multiplies
 * dimensions for every farm that has none. MCO-463 found a second multiplying case (2 axes × 2)
 * and kept flat anyway; it is the [kind] that cannot be flattened away, since a farm mixing a
 * build-time axis with a runtime one produces a list where some entries are switchable and some
 * are not.
 *
 * Most ideas have exactly one mode. [isImplicit] marks the one created for them without asking,
 * so the UI can keep saying nothing about modes until there are two.
 */
data class IdeaProductionMode(
    val id: Int,
    val name: String,
    val position: Int,
    /**
     * Item id -> items per hour, or null where the author knows *what* it makes but has never
     * measured how fast. A missing rate is information; an invented one is not.
     */
    val rates: Map<String, Int?>,
    val kind: IdeaModeKind = IdeaModeKind.Default,
    /**
     * What this variant costs to build, item id -> quantity — populated only for [BUILD_TIME]
     * modes, and only by the reads that ask for it.
     *
     * A whole list, not a delta against the idea's base list: a build-time variant arrives as its
     * own `.litematic`, so a complete list is what the front door produces. When this is
     * non-empty it *replaces* the idea's base material list rather than adding to it.
     *
     * Empty is therefore ambiguous on purpose — it means either "runtime mode, no such thing" or
     * "this read did not fetch requirements". Callers that need the distinction have [kind].
     */
    val requirements: Map<String, Int> = emptyMap(),
) {
    val isImplicit: Boolean get() = name == DEFAULT_MODE_NAME

    /** Whether this mode is fixed when the design is built rather than switchable afterwards. */
    val isBuildTime: Boolean get() = kind == IdeaModeKind.BUILD_TIME

    companion object {
        /**
         * The name given to the single mode created for an idea that never mentioned modes.
         * Only ever shown when a second mode exists to contrast it with.
         */
        const val DEFAULT_MODE_NAME = "Default"
    }
}

/**
 * The build-time modes among these, in author order — the ones an import has to make a choice
 * between, because each costs something different to build.
 */
fun List<IdeaProductionMode>.buildTimeModes(): List<IdeaProductionMode> = filter { it.isBuildTime }

/**
 * The runtime modes among these — the ones that follow an import to the project and stay
 * switchable there (MCO-413).
 */
fun List<IdeaProductionMode>.runtimeModes(): List<IdeaProductionMode> = filterNot { it.isBuildTime }

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

/**
 * Whether any mode makes [itemId] at all, measured or not.
 *
 * Separate from [bestRateFor] because the two answer different questions and a farm can answer
 * one without the other: an unmeasured bamboo farm plainly produces bamboo, and a suggestion that
 * ignored it because no one timed it would be hiding the design for the wrong reason. Whether a
 * *quantity* can be quoted is [bestRateFor]'s business.
 */
fun List<IdeaProductionMode>.produces(itemId: String): Boolean =
    any { mode -> mode.rates.containsKey(itemId) }
