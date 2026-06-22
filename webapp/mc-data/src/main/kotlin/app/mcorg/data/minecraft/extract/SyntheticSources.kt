package app.mcorg.data.minecraft.extract

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.model.resources.ResourceSource.SourceType

/**
 * Hardcoded acquisition sources for items Mojang's data files don't describe — game
 * mechanics (place concrete powder next to water), tool-based world collection (fill a
 * bucket, break ice), bee harvesting, and the wither's nether star.
 *
 * These are plain [ResourceSource]s appended to the extracted recipe/loot/trade sources in
 * [ExtractResourceSources]; they are stored and graph-built exactly like real ones, so the
 * planner needs no special-casing. Filenames use a `synthetic/` prefix so they never collide
 * with real loot/recipe files and read clearly in the drill.
 *
 * Item display names are left blank; they resolve from the version's item catalog on load
 * (`LoadResourceSourcesForVersionStep`), the same as recipe-parser output.
 */
object SyntheticSources {

    /** The 16 dye colours, each with a `<color>_concrete_powder` -> `<color>_concrete` mechanic. */
    private val DYE_COLORS = listOf(
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
    )

    fun all(): List<ResourceSource> = buildList {
        // The wither drops a nether star — no normal loot table.
        add(
            source(
                "synthetic/wither.json", SourceType.LootTypes.ENTITY,
                produces = item("minecraft:nether_star") to ResourceQuantity.ExpectedYield(1.0)
            )
        )

        // Bees: shear a full beehive for honeycomb, or bottle it for a honey bottle.
        add(source("synthetic/beehive_shear.json", SourceType.LootTypes.SHEARING, produces = produce("minecraft:honeycomb", 3)))
        add(
            source(
                "synthetic/beehive_bottle.json", SourceType.LootTypes.BLOCK_INTERACT,
                produces = produce("minecraft:honey_bottle"),
                requires = listOf(require("minecraft:glass_bottle")),
            )
        )

        // Water: fill a bucket from a world source, or break naturally-occurring ice (it melts
        // to water when broken without silk touch). Lava: fill a bucket from a world source.
        add(source("synthetic/water.json", SourceType.MechanicTypes.COLLECT, produces = produce("minecraft:water")))
        add(source("synthetic/ice.json", SourceType.LootTypes.BLOCK, produces = produce("minecraft:water")))
        add(source("synthetic/lava.json", SourceType.MechanicTypes.COLLECT, produces = produce("minecraft:lava")))

        // Concrete: place the matching powder next to water to harden it.
        DYE_COLORS.forEach { color ->
            add(
                source(
                    "synthetic/${color}_concrete.json", SourceType.MechanicTypes.GAME_MECHANIC,
                    produces = produce("minecraft:${color}_concrete"),
                    requires = listOf(require("minecraft:${color}_concrete_powder")),
                )
            )
        }
    }

    private fun item(id: String) = Item(id, "")
    private fun produce(id: String, count: Int = 1): Pair<Item, ResourceQuantity> = item(id) to ResourceQuantity.ItemQuantity(count)
    private fun require(id: String, count: Int = 1): Pair<Item, ResourceQuantity> = item(id) to ResourceQuantity.ItemQuantity(count)

    private fun source(
        filename: String,
        type: SourceType,
        produces: Pair<Item, ResourceQuantity>,
        requires: List<Pair<Item, ResourceQuantity>> = emptyList(),
    ) = ResourceSource(type = type, filename = filename, requiredItems = requires, producedItems = listOf(produces))
}
