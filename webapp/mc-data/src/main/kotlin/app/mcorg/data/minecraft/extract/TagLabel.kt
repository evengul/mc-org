package app.mcorg.data.minecraft.extract

/**
 * The label a variant question is asked under, derived from **what is being chosen between**
 * rather than from the tag's id (MCO-489).
 *
 * A tag in a plan is a question: pick one of these. `#minecraft:smelts_to_glass` names what
 * Mojang uses the tag *for*, which is a fact about the recipe system — "Smelts To Glass" is
 * shown to a user who is being asked to choose between red sand and sand, and names neither.
 * Its neighbours were the same: "Soul Fire Base Blocks", "Stone Crafting Materials", "Coals".
 * MCO-486 made that worse by canonicalising a synthetic set onto the vanilla tag, which dragged
 * the vanilla *name* along with the id and turned "Red Sand or Sand" into "Smelts To Glass".
 *
 * Uniformly member-derived, not "keep the well-named vanilla tags": which names read well is a
 * per-tag judgement that changes with every Minecraft version and has no list to maintain, and
 * the whole failure above was a label that moved when an id did. A pure function of the member
 * *set* cannot move — the same set renders the same string under `#mcorg:choice/red_sand_sand`,
 * `#minecraft:smelts_to_glass`, or anything else it is later folded onto. Ordering is by id for
 * the same reason: the label must not depend on the order some JSON file happened to list them.
 *
 * Derived from the ids, not the display-name catalog, because the catalog's names carry the
 * " (Block)"/" (Item)" disambiguator that `cleanNames` appends — "Red Sand (Block) or Sand
 * (Block)" is not the improvement. Minecraft ids are the names in snake_case anyway.
 *
 * Long sets summarise: the id and name columns are `VARCHAR(100)`, and a sixteen-way list is not
 * a readable question either. Two named options still beat none.
 */
internal fun tagChoiceName(memberIds: Collection<String>): String? {
    val names = memberIds.distinct().sorted().map { prettifyLocalId(it.substringAfterLast(':')) }
    if (names.isEmpty()) return null

    val listed = when (names.size) {
        1 -> names.single()
        else -> names.dropLast(1).joinToString(", ") + " or " + names.last()
    }
    if (listed.length <= MAX_TAG_NAME) return listed

    return "${names.size} options: " + names.take(2).joinToString(", ") + ", …"
}

/** Keep the name under the `minecraft_tag.name` column's 100 chars. */
private const val MAX_TAG_NAME = 96

private fun prettifyLocalId(local: String): String =
    local.split('_').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }
