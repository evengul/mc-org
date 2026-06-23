package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import java.security.MessageDigest

/**
 * Builds a synthetic "any of these" tag for a recipe ingredient expressed as an inline list of
 * alternatives — TNT's `#` slot is `[minecraft:sand, minecraft:red_sand]`, which is a genuine
 * user choice and has no covering vanilla tag (`#minecraft:sand` also includes suspicious_sand).
 *
 * The tag rides the engine's existing OPEN_TAG path: without a [app.mcorg.engine.plan.PlanOverrides]
 * choice it surfaces for the user to pick a member. The id is deterministic in the `mcorg:choice/`
 * namespace so the same alternative-set collapses to one node across recipes and is stable across
 * re-ingests. Small sets get a readable slug (`#mcorg:choice/red_sand_sand`); large ones (e.g. a
 * 16-wool "any wool" recipe) fall back to a content hash so the id and name fit the
 * `minecraft_tag` VARCHAR(100) columns. Member display names are left blank; they resolve from the
 * catalog on load. The tag's own name is stored as-is, so it carries a readable (or summarised) one.
 *
 * Callers pass this tag as a recipe's required ingredient; ExtractMinecraftDataStep already lifts
 * source-referenced ids into ServerData.items, so the tag's members persist with no extra plumbing.
 */
/**
 * Resolves a recipe ingredient slot's resolved member ids to a single [MinecraftId]: an empty set is
 * `null` (slot dropped), a single member is that item/tag as-is, and two or more collapse to a synthetic
 * [choiceTag] the user resolves. Shared by the shaped/shapeless/simple parsers so the "any of" collapse
 * is defined once.
 */
internal fun choiceFrom(memberIds: List<String>): MinecraftId? = when {
    memberIds.isEmpty() -> null
    memberIds.size == 1 -> MinecraftIdFactory.minecraftIdFromId(memberIds.single())
    else -> choiceTag(memberIds)
}

internal fun choiceTag(memberIds: List<String>): MinecraftTag {
    val sorted = memberIds.distinct().sorted()
    val locals = sorted.map { it.substringAfterLast(':') }

    val readableSlug = locals.joinToString("_")
    val slug = if (readableSlug.length <= MAX_SLUG) readableSlug else hash(sorted)
    val id = "#mcorg:choice/$slug"

    val readableName = locals.joinToString(" or ") { prettify(it) }
    val name = if (readableName.length <= MAX_NAME) readableName
    else "${sorted.size} options: " + locals.take(2).joinToString(", ") { prettify(it) } + ", …"

    return MinecraftTag(id, name, sorted.map { Item(it, "") })
}

/** Keep the id under the tag column's 100 chars ("#mcorg:choice/" is 14). */
private const val MAX_SLUG = 80
private const val MAX_NAME = 96

private fun prettify(local: String): String =
    local.split('_').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }

/** Stable, short hex digest of the sorted member ids — deterministic across JVMs and re-ingests. */
private fun hash(sortedIds: List<String>): String =
    MessageDigest.getInstance("SHA-256")
        .digest(sortedIds.joinToString(",").toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(16)
