package app.mcorg.data.minecraft.extract

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.model.resources.ResourceSource.SourceType

/**
 * Hardcoded acquisition sources for items Mojang's data files don't describe — in-world
 * transforms (place concrete powder next to water, strip a log, make mud), tool-based world
 * collection (fill a bucket, break ice), bee harvesting, and the wither's nether star.
 *
 * These are plain [ResourceSource]s appended to the extracted recipe/loot/trade sources in
 * [ExtractResourceSources]; they are stored and graph-built exactly like real ones, so the
 * planner needs no special-casing. Filenames use a `synthetic/` prefix so they never collide
 * with real loot/recipe files and read clearly in the drill.
 *
 * Item display names are left blank; they resolve from the version's item catalog on load
 * (`LoadResourceSourcesForVersionStep`), the same as recipe-parser output.
 *
 * **Version-filtered.** [all] takes the version's item registry and drops any entry naming an
 * id that version doesn't have, so 1.18 never gains a phantom `stripped_cherry_log`. Entries
 * are therefore written for the newest version and prune themselves backwards — the further
 * back the supported range goes, the more this carries.
 *
 * **Relationship to `minecraft:block_transformer` (26.3+).** That component is the data-driven
 * form of [SourceType.MechanicTypes.IN_WORLD_TRANSFORM] and will eventually let us extract log
 * stripping, tilling and path-making instead of hardcoding them — but *only for 26.3 and up*.
 * Everything below stays on this registry permanently, so a future extractor supersedes these
 * entries per-version rather than replacing them. See
 * `documentation/work-documents/gathering-planner/block-transformer-analysis.md`.
 */
object SyntheticSources {

    /** The 16 dye colours, each with a `<color>_concrete_powder` -> `<color>_concrete` mechanic. */
    private val DYE_COLORS = listOf(
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
    )

    /**
     * Blocks an axe strips into a `stripped_` counterpart. Only these are listed: the six-sided
     * `*_wood` / `*_hyphae` variants are craftable from their stripped log, so the recipe
     * extractor already covers them and they are not circular in the graph.
     */
    private val STRIPPABLE = listOf(
        "oak_log", "birch_log", "spruce_log", "jungle_log", "acacia_log", "dark_oak_log",
        "mangrove_log", "cherry_log", "pale_oak_log",
        "crimson_stem", "warped_stem",
        "bamboo_block",
    )

    /**
     * @param itemIds the version's item registry ([ExtractionContext.itemIds]). Entries naming
     *   an id outside it are dropped, so each version only gets sources it can actually use.
     */
    fun all(itemIds: Set<String>): List<ResourceSource> =
        allUnfiltered().filter { source ->
            (source.producedItems + source.requiredItems).all { (id, _) -> id.id in itemIds }
        }

    /** Every entry, before version filtering. Visible for tests. */
    internal fun allUnfiltered(): List<ResourceSource> = buildList {
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

        // The *filled bucket items*, which is what a schematic's material list actually names —
        // distinct from the fluid ids above. Without these, `lava_bucket` had no source at all
        // and `water_bucket`'s best path was chest loot from a trial chamber.
        //
        // No `bucket` requirement, deliberately: placing the fluid returns the empty bucket, so
        // one bucket serves an arbitrary number of placements. Requiring one per filled bucket
        // would over-count iron enormously on any build with water. Same "tools are not
        // materials" rule the design doc applies to shears and axes.
        add(source("synthetic/water_bucket.json", SourceType.MechanicTypes.COLLECT, produces = produce("minecraft:water_bucket")))
        add(source("synthetic/lava_bucket.json", SourceType.MechanicTypes.COLLECT, produces = produce("minecraft:lava_bucket")))
        add(source("synthetic/powder_snow_bucket.json", SourceType.MechanicTypes.COLLECT, produces = produce("minecraft:powder_snow_bucket")))

        // Strip a log with an axe. Until now the only source for every `stripped_*` was breaking
        // one — perfectly circular advice. The axe is a tool, so the log is the only input.
        STRIPPABLE.forEach { base ->
            add(
                source(
                    "synthetic/stripped_$base.json", SourceType.MechanicTypes.IN_WORLD_TRANSFORM,
                    produces = produce("minecraft:stripped_$base"),
                    requires = listOf(require("minecraft:$base")),
                )
            )
        }

        // Use a water bottle on dirt. The bottle empties rather than being consumed, so — like
        // the bucket above — it is a tool, and dirt is the only material.
        add(
            source(
                "synthetic/mud.json", SourceType.MechanicTypes.IN_WORLD_TRANSFORM,
                produces = produce("minecraft:mud"),
                requires = listOf(require("minecraft:dirt")),
            )
        )

        // Shovel on dirt/grass, hoe on dirt. Both are ordinary build blocks that had no source
        // at all — they are code-defined interactions, absent from recipe and loot JSON alike.
        add(
            source(
                "synthetic/dirt_path.json", SourceType.MechanicTypes.IN_WORLD_TRANSFORM,
                produces = produce("minecraft:dirt_path"),
                requires = listOf(require("minecraft:dirt")),
            )
        )
        add(
            source(
                "synthetic/farmland.json", SourceType.MechanicTypes.IN_WORLD_TRANSFORM,
                produces = produce("minecraft:farmland"),
                requires = listOf(require("minecraft:dirt")),
            )
        )

        // Concrete: place the matching powder next to water to harden it.
        DYE_COLORS.forEach { color ->
            add(
                source(
                    "synthetic/${color}_concrete.json", SourceType.MechanicTypes.IN_WORLD_TRANSFORM,
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
