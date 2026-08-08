package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item

/**
 * MCO-304: item *families*, for moving a whole material list from oak to spruce or from blue
 * to red in one action, while the list is still a form.
 *
 * The unit of work is a **variant token** — the leading `_`-separated part of an id naming
 * which member of a family it is: `oak` in `oak_planks`, `light_blue` in
 * `light_blue_terracotta`. Swapping a family means replacing that token across every row that
 * carries it.
 *
 * **Why not tags.** The issue asked to check `mc-data`'s tag extraction first, and the check
 * comes back negative for the case it was asked about. A tag is only a graph node when some
 * recipe or loot source references it as a choice ingredient (see [findVariantCandidates] and
 * `mc-engine/CLAUDE.md`), and vanilla has no "any terracotta" ingredient anywhere — dyeing
 * takes plain `minecraft:terracotta` plus a dye. So "all blue terracotta -> red", the issue's
 * own headline example, is invisible to tags. Doors, per [findVariantCandidates], are missing
 * for the same reason.
 *
 * **What is used instead** is the world's own item catalog, read the same way a tag would have
 * been ([variantTokens]): ids that agree on everything except a leading token *are* a family,
 * and vanilla naming says so consistently. Nothing is hardcoded and nothing is invented — no
 * colour list, no wood list; the catalog for the world's version is the whole input, so a
 * version that adds a wood species gets it for free.
 *
 * This is demand-side. Substitution edits *what you want*; the scorer must never "optimize" it
 * and nothing here touches how the planner sources anything (MCO-289's standing principle).
 */

/** A family present in one material list: the rows sharing a variant token, and where they can go. */
data class SubstitutionFamily(
    /** The shared token, e.g. `oak` or `light_blue`. */
    val token: String,
    /** Human-facing name for the token, e.g. "Dark Oak". */
    val label: String,
    /** The ids carrying [token], in the order they were given. */
    val itemIds: List<String>,
    /**
     * Tokens this family can move to *in full* — every row in [itemIds] has a catalog item
     * under the target token. Partial targets are withheld on purpose: "swap all oak to
     * crimson" must not quietly leave three oak rows behind because crimson has no sapling.
     */
    val targets: List<SubstitutionTarget>,
)

data class SubstitutionTarget(val token: String, val label: String)

/** A family needs this many members before the catalog is willing to call it one. */
private const val MIN_FAMILY_SIZE = 4

/**
 * The families worth offering a batch swap for, given a review screen's rows.
 *
 * A family needs at least two rows — one row is MCO-246's per-row variant swap, which already
 * exists and does not need a batch control — and at least one target it can move to whole.
 */
fun findSubstitutionFamilies(
    catalog: List<Item>,
    requirements: Collection<String>,
    tokens: Set<String> = variantTokens(catalog),
): List<SubstitutionFamily> {
    if (tokens.isEmpty()) return emptyList()

    val catalogIds = catalog.mapTo(HashSet()) { it.id }
    val byToken = LinkedHashMap<String, MutableList<Pair<String, String>>>()

    for (itemId in requirements) {
        val (token, remainder) = splitVariant(itemId, tokens) ?: continue
        byToken.getOrPut(token) { mutableListOf() }.add(itemId to remainder)
    }

    return byToken.mapNotNull { (token, rows) ->
        if (rows.size < 2) return@mapNotNull null
        val targets = tokens
            .asSequence()
            .filter { it != token }
            .filter { candidate -> rows.all { (_, remainder) -> joinVariant(candidate, remainder) in catalogIds } }
            .map { SubstitutionTarget(it, labelOf(it)) }
            .sortedBy { it.label }
            .toList()
        if (targets.isEmpty()) null
        else SubstitutionFamily(token, labelOf(token), rows.map { it.first }, targets)
    }.sortedBy { it.label }
}

/**
 * Rewrites [requirements], moving every row carrying [fromToken] onto [toToken].
 *
 * Rows with no catalog item under the target token are left alone rather than dropped —
 * [findSubstitutionFamilies] only offers targets where every row maps, so this should not
 * happen from the UI, and silently losing a row is the one outcome nobody could recover from.
 *
 * Quantities merge where a swap lands on an id the list already holds (swapping oak to spruce
 * in a list that already has spruce planks): two rows for one item is not a material list.
 * A swapped row keeps the position of the row it replaced.
 */
fun applySubstitution(
    catalog: List<Item>,
    requirements: Map<Item, Int>,
    fromToken: String,
    toToken: String,
    tokens: Set<String> = variantTokens(catalog),
): Map<Item, Int> {
    val byId = catalog.associateBy { it.id }
    val result = LinkedHashMap<Item, Int>()

    for ((item, amount) in requirements) {
        // Falls back to the row itself when the id is not in the catalog at all — the caller
        // validates ids, and a lookup miss must not cost the row.
        val target = byId[substitutedId(byId.keys, item.id, fromToken, toToken, tokens)] ?: item
        result[target] = (result[target] ?: 0) + amount
    }

    return result
}

/**
 * Where a single id lands under a [fromToken] -> [toToken] swap, or the id itself when the
 * swap does not touch it or [catalogIds] has no such target.
 *
 * Exposed so a caller can follow something *attached* to a row — the review screen tracks
 * which rows are excluded, and an exclusion has to move with the row it was applied to.
 */
fun substitutedId(
    catalogIds: Set<String>,
    itemId: String,
    fromToken: String,
    toToken: String,
    tokens: Set<String>,
): String {
    val remainder = splitVariant(itemId, tokens)?.takeIf { it.first == fromToken }?.second
        ?: return itemId
    return joinVariant(toToken, remainder).takeIf { it in catalogIds } ?: itemId
}

/**
 * The variant vocabulary a version's item catalog spells out.
 *
 * Ids are keyed by what follows their leading token, and a key reached by [MIN_FAMILY_SIZE] or
 * more distinct leading tokens is a family: `planks` is reached by oak, spruce, birch...;
 * `terracotta` by fourteen colours. Those leading tokens are the vocabulary.
 *
 * Two-token variants (`dark_oak`, `light_blue`) need a second pass, because a single-token
 * split puts `dark_oak_planks` under the key `oak_planks` where it has only `pale_oak` for
 * company. So an id whose single-token key landed in a group too small to be a family gets to
 * try again one token deeper — which files `dark_oak` under `planks` alongside `oak`, and
 * `light_blue` under `terracotta` alongside `blue`.
 *
 * The retry is conditional for a reason: applied unconditionally it would also file
 * `white_stained` under `glass_pane`, and then a list holding both `white_wool` and
 * `white_stained_glass_pane` would see two different "white" families instead of one.
 */
fun variantTokens(catalog: List<Item>): Set<String> {
    val split = catalog.mapNotNull { item ->
        val name = item.id.substringAfter(':').takeIf { item.id.contains(':') } ?: return@mapNotNull null
        name.split('_').takeIf { it.size >= 2 }
    }

    fun key(tokens: List<String>, prefixLength: Int) = tokens.drop(prefixLength).joinToString("_")

    val groups = LinkedHashMap<String, MutableSet<String>>()
    split.forEach { tokens ->
        groups.getOrPut(key(tokens, 1)) { linkedSetOf() }.add(tokens.first())
    }

    // Snapshot before the second pass, which writes into the same map: "was this id already
    // in a family?" has to be answered against the first pass alone, or the answer would
    // depend on catalog order.
    val singleTokenGroupSize = groups.mapValues { (_, prefixes) -> prefixes.size }
    split.filter { it.size >= 3 }.forEach { tokens ->
        if ((singleTokenGroupSize[key(tokens, 1)] ?: 0) >= MIN_FAMILY_SIZE) return@forEach
        groups.getOrPut(key(tokens, 2)) { linkedSetOf() }.add(tokens.take(2).joinToString("_"))
    }

    return groups.values.filterTo(mutableListOf()) { it.size >= MIN_FAMILY_SIZE }.flatten().toSet()
}

/**
 * Splits an id into (variant token, remainder), longest token first.
 *
 * Longest-first is what keeps `dark_oak_planks` out of the `oak` family — its tokens are
 * `[dark, oak, planks]`, so `oak` is not a token-aligned prefix at all — and what picks
 * `light_blue` over a bare `light` on `light_blue_terracotta`.
 */
private fun splitVariant(itemId: String, tokens: Set<String>): Pair<String, String>? {
    if (!itemId.contains(':')) return null
    val namespace = itemId.substringBefore(':')
    val name = itemId.substringAfter(':')

    return tokens
        .filter { name.startsWith("${it}_") }
        .maxByOrNull { it.length }
        ?.let { token -> token to "$namespace:${name.removePrefix("${token}_")}" }
}

/** [splitVariant]'s inverse — the remainder carries the namespace, so it goes back in front. */
private fun joinVariant(token: String, remainder: String): String =
    "${remainder.substringBefore(':')}:${token}_${remainder.substringAfter(':')}"

private fun labelOf(token: String): String =
    token.split('_').joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
