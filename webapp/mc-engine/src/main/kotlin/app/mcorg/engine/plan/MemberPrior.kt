package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.MinecraftId

/**
 * A canonical-default ordering over the *members* of a choice (an open tag or a synthetic
 * `#mcorg:choice/…` set), used purely as a **tiebreak** when two members are otherwise equally
 * good. Minecraft offers many cosmetically-equivalent alternatives for one ingredient slot —
 * TNT accepts `sand` or `red_sand`, a plank recipe accepts any of twelve woods — and the scorer
 * cannot separate them because they are structurally identical in the graph. Left to an
 * alphabetical tiebreak the *less* canonical member wins ("Red Sand" before "Sand", black before
 * white, acacia before oak). This prior fixes that by encoding the obvious default.
 *
 * It is **not** an abundance metric and reads no world data; the defaults are human judgments about
 * which variant a player reaches for first. It is intentionally a tiebreak only: callers compare by
 * source score first and fall back to this when scores tie (see [comparator]). At bulk demand, where
 * the scorer genuinely separates members (e.g. charcoal's smelt recipe earning the recipe-threshold
 * bonus over mined coal), the score decides and this prior never fires.
 *
 * The ordering is **axis-based**, not per-tag: a handful of controlled vocabularies parsed from the
 * item id (wood species, wood form, dye colour, stone base, decorative form) cover ~80 distinct
 * choice points and survive Minecraft version bumps — a thirteenth wood ranks correctly with no new
 * entry. Members in a given choice set are homogeneous along all but one axis, so only the axis that
 * varies decides; the rest are constant and drop out of the comparison.
 *
 * Lower rank = more canonical = preferred first.
 */
object MemberPrior {

    /** Compares two ids by their [RankKey]; more-canonical first. */
    fun compare(a: String, b: String): Int = rankKey(a).compareTo(rankKey(b))

    /**
     * A [Comparator] over [MinecraftId] for use as a tiebreak. Chain it *after* the primary
     * score comparator so the prior only orders members the scorer rates equally:
     * ```
     * compareByDescending<RankedMember> { it.score }
     *     .then(MemberPrior.comparator { it.member })
     *     .thenBy { it.member.name }
     * ```
     */
    fun <T> comparator(select: (T) -> MinecraftId): Comparator<T> =
        Comparator { x, y -> compare(select(x).id, select(y).id) }

    /**
     * The composite rank for one item id. Axes are compared in priority order: an explicit pair
     * always wins (so coal/charcoal, soul_sand/soul_soil are unambiguous), then wood species, then
     * wood form, then colour, then stone base, then decorative form. Within a homogeneous choice set
     * only the differentiating axis is non-constant, so the trailing order rarely matters — but
     * species deliberately precedes form so the `#minecraft:logs` set (species *and* form vary)
     * prefers the canonical species even in a less canonical form.
     */
    internal fun rankKey(id: String): RankKey {
        val local = id.substringAfterLast(':')
        val tokens = local.split('_')
        return RankKey(
            explicit = EXPLICIT[local] ?: UNRANKED,
            species = woodSpeciesRank(local),
            woodForm = woodFormRank(tokens),
            colour = colourRank(local),
            stone = STONE_BASE[local] ?: UNRANKED,
            decorForm = decorFormRank(tokens),
        )
    }

    /** Sentinel for "this axis does not apply" — constant within a homogeneous set, so it drops out. */
    private const val UNRANKED = Int.MAX_VALUE

    /**
     * Explicit pairs the structural axes can't derive. Each maps to an intra-pair order (0 = default).
     * coal beats charcoal at a score tie (low demand); soul_sand beats soul_soil.
     */
    private val EXPLICIT: Map<String, Int> = mapOf(
        "coal" to 0, "charcoal" to 1,
        "soul_sand" to 0, "soul_soil" to 1,
    )

    /** Cobblestone is the everyday crafting stone; deepslate and blackstone are biome-gated. */
    private val STONE_BASE: Map<String, Int> = mapOf(
        "cobblestone" to 0, "cobbled_deepslate" to 1, "blackstone" to 2,
    )

    /**
     * Wood species in canonical rank order, oak first — the list index *is* the rank.
     * `contains` (not prefix) because the species sits mid-id in stripped forms
     * (`stripped_oak_log`).
     */
    private val WOOD_SPECIES: List<String> = listOf(
        "oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
        "mangrove", "cherry", "pale_oak", "bamboo", "crimson", "warped",
    )

    /**
     * Match order is by descending name length, decoupled from rank order: `dark_oak`/`pale_oak`
     * must be tested before `oak` so the substring match resolves to the longer (correct) species,
     * even though oak ranks first.
     */
    private val WOOD_SPECIES_BY_MATCH_LENGTH: List<String> = WOOD_SPECIES.sortedByDescending { it.length }

    private fun woodSpeciesRank(local: String): Int {
        val match = WOOD_SPECIES_BY_MATCH_LENGTH.firstOrNull { local.contains(it) } ?: return UNRANKED
        return WOOD_SPECIES.indexOf(match)
    }

    /**
     * Form of a log-family block: plain log/stem/block beats wood/hyphae, and unstripped beats
     * stripped. Base 0 (not [UNRANKED]) so non-wood members are all equal here and the colour axis
     * decides instead.
     */
    private fun woodFormRank(tokens: List<String>): Int {
        val stripped = "stripped" in tokens
        val secondary = "wood" in tokens || "hyphae" in tokens
        val isWoodForm = secondary || "log" in tokens || "stem" in tokens || "block" in tokens
        if (!isWoodForm) return 0
        return (if (stripped) 2 else 0) + (if (secondary) 1 else 0)
    }

    /**
     * Dye colours in Minecraft's canonical order, white first. An id carrying no colour prefix is
     * "uncoloured" and ranks ahead of every colour (rank 0) — so `sand` beats `red_sand`, plain
     * `bundle`/`shulker_box` beat their dyed variants, and `egg` beats `blue_egg`. Detected as a
     * `"<colour>_"` prefix, which is anchored (so `blackstone` is not colour-black and
     * `light_gray_*` is not gray).
     */
    private val COLOURS: List<String> = listOf(
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
    )

    private fun colourRank(local: String): Int {
        val idx = COLOURS.indexOfFirst { local.startsWith("${it}_") }
        return if (idx >= 0) 1 + idx else 0
    }

    /** Decorative stone form: base block, then cut, chiseled, pillar. Token-matched to avoid false hits. */
    private fun decorFormRank(tokens: List<String>): Int = when {
        "chiseled" in tokens -> 2
        "pillar" in tokens -> 3
        "cut" in tokens -> 1
        else -> 0
    }

    /**
     * Composite rank compared field-by-field in priority order. Smaller is more canonical.
     */
    internal data class RankKey(
        val explicit: Int,
        val species: Int,
        val woodForm: Int,
        val colour: Int,
        val stone: Int,
        val decorForm: Int,
    ) : Comparable<RankKey> {
        override fun compareTo(other: RankKey): Int = compareValuesBy(
            this, other,
            { it.explicit }, { it.species }, { it.woodForm }, { it.colour }, { it.stone }, { it.decorForm },
        )
    }
}
