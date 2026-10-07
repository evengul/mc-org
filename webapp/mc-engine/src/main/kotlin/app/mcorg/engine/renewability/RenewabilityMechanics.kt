package app.mcorg.engine.renewability

/**
 * A way the world makes an item that no data file describes: a tree grows, lava meets water,
 * copper ages. [produces] becomes renewable once everything in [requires] is.
 *
 * **Never a bare seed for a conversion.** Grass spreading turns dirt into grass, so it requires
 * dirt; written without the input, it would prove dirt renewable by the back door, which is
 * exactly what seeding nylium spread did to netherrack.
 *
 * These prove renewability only. They are not sources: the planner never sees them, nothing
 * prices them, and they never belong in `SyntheticSources`.
 */
data class Mechanic(
    val family: String,
    val produces: Set<String>,
    val requires: Set<String> = emptySet(),
)

/**
 * The hand-written half of [Renewability] (MCO-565).
 *
 * Written once for the newest version, as item ids rather than a per-version list, and pruned by
 * each version's registry: an output the version lacks is dropped, and a mechanic whose input the
 * version lacks never fires. Where the data names a family, the family is read from it — tree
 * growth takes `#minecraft:logs` and `#minecraft:leaves`, which is how 26.3's poplar arrived
 * without anyone listing it.
 */
object RenewabilityMechanics {

    private fun m(vararg ids: String): Set<String> = ids.mapTo(linkedSetOf()) { "minecraft:$it" }

    /** `family` names are what the review page keys accepted sources on; renaming one un-accepts it. */
    private val FIXED = listOf(
        Mechanic("cobble/stone/basalt generator", m("cobblestone")),
        Mechanic("cobble/stone/basalt generator", m("stone")),
        Mechanic("cobble/stone/basalt generator", m("basalt")),
        Mechanic("tree growth", m("mangrove_roots", "pale_hanging_moss")),
        Mechanic("huge fungus", m("nether_wart_block", "warped_wart_block", "shroomlight")),
        Mechanic("huge mushroom", m("mushroom_stem", "brown_mushroom_block", "red_mushroom_block")),
        Mechanic("chorus growth", m("chorus_fruit", "chorus_flower")),
        Mechanic("water freezing", m("ice")),
        Mechanic(
            "budding amethyst growth",
            m("amethyst_shard", "small_amethyst_bud", "medium_amethyst_bud", "large_amethyst_bud", "amethyst_cluster"),
        ),
        Mechanic("sculk spread", m("sculk", "sculk_vein"), m("stone")),
        Mechanic("vine/lichen growth", m("twisting_vines", "weeping_vines", "vine", "glow_lichen")),
        Mechanic("cave vine growth", m("glow_berries")),
        Mechanic("mushroom spread", m("brown_mushroom", "red_mushroom")),
        Mechanic("bone meal underwater", m("seagrass"), m("bone_meal")),
        Mechanic(
            "bone meal on nylium (nylium is not consumed)",
            m("crimson_roots", "warped_roots", "nether_sprouts", "crimson_fungus", "warped_fungus"),
            m("bone_meal"),
        ),
        Mechanic("grass spread (converts dirt)", m("grass_block"), m("dirt")),
        Mechanic("mycelium spread (converts dirt)", m("mycelium"), m("dirt")),
        Mechanic("bone meal on grass", m("short_grass", "tall_grass", "fern", "large_fern"), m("bone_meal", "grass_block")),
        // The ingest keeps no flower tag for 26.x, so the bone-meal flowers are listed by hand.
        Mechanic(
            "bone meal on grass (flower farm)",
            m(
                "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
                "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley",
            ),
            m("bone_meal", "grass_block"),
        ),
        Mechanic("bone meal duplicates it", m("pink_petals", "wildflowers"), m("bone_meal")),
        Mechanic("bone meal duplicates tall flowers", m("lilac", "peony", "rose_bush", "sunflower"), m("bone_meal")),
        // stone farm -> moss -> azalea -> rooted dirt -> dirt -> mud -> clay
        Mechanic("moss spread (converts stone)", m("moss_block"), m("bone_meal", "stone")),
        Mechanic("pale moss spread (converts stone)", m("pale_moss_block"), m("bone_meal", "stone")),
        Mechanic("azalea tree growth (block below -> rooted dirt)", m("rooted_dirt"), m("azalea", "bone_meal", "moss_block")),
        Mechanic("till rooted dirt (-> dirt)", m("dirt"), m("rooted_dirt")),
        Mechanic("mud dries over pointed dripstone", m("clay"), m("mud")),
        Mechanic("anvil wear (using or dropping it)", m("chipped_anvil"), m("anvil")),
        Mechanic("anvil wear (using or dropping it)", m("damaged_anvil"), m("chipped_anvil")),
        Mechanic("copper golem oxidises into a statue", m("copper_golem_statue"), m("copper_block", "carved_pumpkin")),
        Mechanic("sniffers breed (torchflower seeds)", m("sniffer_egg"), m("torchflower_seeds")),
        Mechanic("turtles breed (seagrass)", m("turtle_egg"), m("seagrass")),
        Mechanic("a mob killed by a wither drops a wither rose", m("wither_rose"), m("wither_skeleton_skull", "soul_sand")),
        Mechanic("torchflower grows from its seeds", m("torchflower"), m("torchflower_seeds")),
    )

    private val COPPER_STAGES = listOf("" to "exposed_", "exposed_" to "weathered_", "weathered_" to "oxidized_")

    /**
     * The mechanics that exist in a version with [registry] and [tags].
     *
     * Copper ageing is read off the item names — `exposed_X` from `X`, `weathered_X` from
     * `exposed_X`, `oxidized_X` from `weathered_X` — so every copper item a version adds ages
     * without being listed. Bare `copper` is the one name that does not follow the pattern: it
     * ages from `copper_block`.
     */
    fun forVersion(registry: Set<String>, tags: Map<String, Set<String>>): List<Mechanic> {
        val treeGrowth = Mechanic(
            "tree growth (#logs, #leaves)",
            tags["#minecraft:logs"].orEmpty() + tags["#minecraft:leaves"].orEmpty(),
        )
        val copper = registry.sorted().flatMap { id ->
            val name = id.removePrefix("minecraft:")
            COPPER_STAGES.mapNotNull { (previous, stage) ->
                if (!name.startsWith(stage)) return@mapNotNull null
                val rest = name.removePrefix(stage)
                val base = "minecraft:" + previous + if (previous == "" && rest == "copper") "copper_block" else rest
                if (base in registry) Mechanic("copper oxidation", setOf(id), setOf(base)) else null
            }
        }
        return (FIXED.take(3) + treeGrowth + FIXED.drop(3) + copper)
            .filter { it.requires.all(registry::contains) }
            .map { it.copy(produces = it.produces.filterTo(linkedSetOf(), registry::contains)) }
            .filter { it.produces.isNotEmpty() }
    }
}
