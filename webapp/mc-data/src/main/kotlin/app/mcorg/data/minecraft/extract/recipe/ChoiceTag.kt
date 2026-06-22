package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag

/**
 * Builds a synthetic "any of these" tag for a recipe ingredient expressed as an inline list of
 * alternatives — TNT's `#` slot is `[minecraft:sand, minecraft:red_sand]`, which is a genuine
 * user choice and has no covering vanilla tag (`#minecraft:sand` also includes suspicious_sand).
 *
 * The tag rides the engine's existing OPEN_TAG path: without a [app.mcorg.engine.plan.PlanOverrides]
 * choice it surfaces for the user to pick a member. The id is deterministic (sorted member local
 * names under the `mcorg:choice/` namespace) so the same alternative-set collapses to one node
 * across recipes and is stable across re-ingests. Member display names are left blank; they resolve
 * from the item catalog on load, like all recipe-parser output. The tag itself carries a readable
 * name derived from the member ids, since tag names are stored as-is (not catalog-resolved).
 *
 * Callers pass this tag as a recipe's required ingredient; ExtractMinecraftDataStep already lifts
 * source-referenced ids into ServerData.items, so the tag's members persist with no extra plumbing.
 */
internal fun choiceTag(memberIds: List<String>): MinecraftTag {
    val sorted = memberIds.distinct().sorted()
    val locals = sorted.map { it.substringAfterLast(':') }
    val id = "#mcorg:choice/" + locals.joinToString("_")
    val name = locals.joinToString(" or ") { local ->
        local.split('_').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }
    }
    return MinecraftTag(id, name, sorted.map { Item(it, "") })
}
