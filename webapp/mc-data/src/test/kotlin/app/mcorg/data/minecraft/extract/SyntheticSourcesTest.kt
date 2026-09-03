package app.mcorg.data.minecraft.extract

import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyntheticSourcesTest {

    /**
     * An item registry containing every id the entries mention — i.e. "the newest version",
     * where nothing is filtered out. Version filtering is exercised separately below.
     */
    private val allIds: Set<String> =
        SyntheticSources.allUnfiltered().flatMap { s ->
            (s.producedItems + s.requiredItems).map { it.first.id }
        }.toSet()

    private val sources = SyntheticSources.all(allIds)

    private fun producing(itemId: String) =
        sources.filter { s -> s.producedItems.any { it.first.id == itemId } }

    @Test
    fun `every synthetic source is namespaced and produces exactly one item`() {
        assertTrue(sources.isNotEmpty())
        sources.forEach { s ->
            assertTrue(s.filename.startsWith("synthetic/"), "${s.filename} must be under synthetic/")
            assertEquals(1, s.producedItems.size, "${s.filename} should produce one item")
        }
    }

    @Test
    fun `nether star comes from the wither as an entity drop`() {
        val star = producing("minecraft:nether_star").single()
        assertEquals(SourceType.LootTypes.ENTITY, star.type)
        assertEquals("synthetic/wither.json", star.filename)
    }

    @Test
    fun `honeycomb is sheared and honey bottle consumes a glass bottle`() {
        val honeycomb = producing("minecraft:honeycomb").single()
        assertEquals(SourceType.LootTypes.SHEARING, honeycomb.type)
        assertEquals(ResourceQuantity.ItemQuantity(3), honeycomb.producedItems.single().second)

        val bottle = producing("minecraft:honey_bottle").single()
        assertEquals(SourceType.LootTypes.BLOCK_INTERACT, bottle.type)
        assertEquals("minecraft:glass_bottle", bottle.requiredItems.single().first.id)
    }

    @Test
    fun `water has both a collect and an ice source, lava is collected`() {
        val water = producing("minecraft:water")
        assertEquals(2, water.size)
        assertNotNull(water.firstOrNull { it.type == SourceType.MechanicTypes.COLLECT })
        assertNotNull(water.firstOrNull { it.type == SourceType.LootTypes.BLOCK && it.filename == "synthetic/ice.json" })

        val lava = producing("minecraft:lava").single()
        assertEquals(SourceType.MechanicTypes.COLLECT, lava.type)
    }

    @Test
    fun `all sixteen concretes are an in-world transform consuming their powder`() {
        val colors = listOf(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
        )
        colors.forEach { color ->
            val concrete = producing("minecraft:${color}_concrete").single()
            assertEquals(SourceType.MechanicTypes.IN_WORLD_TRANSFORM, concrete.type, "$color concrete type")
            assertEquals("minecraft:${color}_concrete_powder", concrete.requiredItems.single().first.id)
        }
    }

    @Test
    fun `every strippable log has an in-world transform from its unstripped form`() {
        val bases = listOf(
            "oak_log", "birch_log", "spruce_log", "jungle_log", "acacia_log", "dark_oak_log",
            "mangrove_log", "cherry_log", "pale_oak_log", "crimson_stem", "warped_stem",
            "bamboo_block",
        )
        bases.forEach { base ->
            val stripped = producing("minecraft:stripped_$base").single()
            assertEquals(SourceType.MechanicTypes.IN_WORLD_TRANSFORM, stripped.type, "$base strip type")
            assertEquals("minecraft:$base", stripped.requiredItems.single().first.id, "$base strip input")
        }
    }

    @Test
    fun `mud, dirt path and farmland transform dirt`() {
        listOf("minecraft:mud", "minecraft:dirt_path", "minecraft:farmland").forEach { id ->
            val transform = producing(id).single()
            assertEquals(SourceType.MechanicTypes.IN_WORLD_TRANSFORM, transform.type, "$id type")
            assertEquals("minecraft:dirt", transform.requiredItems.single().first.id, "$id input")
        }
    }

    /**
     * The bucket is returned empty on placement, so it is a reusable tool rather than a
     * consumed material — requiring one per filled bucket would badly over-count iron on any
     * build containing water. See the note in [SyntheticSources].
     */
    @Test
    fun `filled buckets are collected and consume nothing`() {
        listOf("water_bucket", "lava_bucket", "powder_snow_bucket").forEach { id ->
            val bucket = producing("minecraft:$id").single()
            assertEquals(SourceType.MechanicTypes.COLLECT, bucket.type, "$id type")
            assertTrue(bucket.requiredItems.isEmpty(), "$id must not consume a bucket")
        }
    }

    /**
     * MCO-467 — the *placed* powder snow block, distinct from the bucket item. It had no source
     * at all, so any build containing it reported "no feasible source found". Pouring empties
     * the bucket rather than consuming it, so the bucket is the input and nothing else is.
     */
    @Test
    fun `placed powder snow is poured from its bucket`() {
        val snow = producing("minecraft:powder_snow").single()

        assertEquals(SourceType.MechanicTypes.IN_WORLD_TRANSFORM, snow.type)
        assertEquals(
            listOf("minecraft:powder_snow_bucket"),
            snow.requiredItems.map { it.first.id },
        )
    }

    /**
     * MCO-467 — 54 nether portal blocks on the YAMS import read as unobtainable. Lighting the
     * frame costs no material of its own: the obsidian is placed blocks the schematic counts
     * separately, and flint and steel is a tool.
     */
    @Test
    fun `nether portal is lit and costs no material`() {
        val portal = producing("minecraft:nether_portal").single()

        assertEquals(SourceType.MechanicTypes.IN_WORLD_TRANSFORM, portal.type)
        assertTrue(
            portal.requiredItems.isEmpty(),
            "the frame's obsidian is counted as its own blocks, and the lighter is a tool",
        )
    }

    /**
     * MCO-492 — nothing in Mojang's data grows a crop, so every item here had exactly one
     * route in the graph: break its own block. Wheat and bread were being planned through
     * chest loot as a result.
     */
    @Test
    fun `every grown crop has a plant-and-wait source`() {
        val crops = listOf(
            "wheat", "carrot", "potato", "beetroot", "melon", "pumpkin", "sugar_cane",
            "bamboo", "cocoa_beans", "nether_wart", "sweet_berries", "kelp", "cactus",
        )
        crops.forEach { crop ->
            val growth = producing("minecraft:$crop").single { it.filename == "synthetic/grow_$crop.json" }
            assertEquals(SourceType.MechanicTypes.IN_WORLD_TRANSFORM, growth.type, "$crop type")
        }
    }

    /**
     * The planting stock comes back with the harvest — wheat drops seeds, a carrot yields
     * several carrots, cane and bamboo and berry bushes regrow — so one starting unit serves
     * an arbitrary quantity, exactly like the bucket above. Requiring one per unit would
     * over-count the input by the whole harvest, and for the self-seeding crops it would be a
     * pure self-cycle the selector rejects outright.
     */
    @Test
    fun `growing a crop consumes nothing`() {
        val growth = sources.filter { it.filename.startsWith("synthetic/grow_") }

        assertEquals(13, growth.size, "expected one entry per grown crop, got ${growth.map { it.filename }}")
        growth.forEach { source ->
            assertTrue(source.requiredItems.isEmpty(), "${source.filename} must consume no material")
        }
    }

    /**
     * The defect this fixes is specifically that a crop's *only* route is breaking itself, so
     * the growth source has to name the crop the block loot names — otherwise it leaves the
     * circle intact and only adds noise.
     */
    @Test
    fun `wheat is grown rather than only broken out of its own block`() {
        val wheat = producing("minecraft:wheat")

        assertEquals(1, wheat.size)
        assertEquals("synthetic/grow_wheat.json", wheat.single().filename)
        assertEquals(SourceType.MechanicTypes.IN_WORLD_TRANSFORM, wheat.single().type)
        assertTrue(
            wheat.single().type.isConstructive(),
            "growth must count as constructive, or the self-block loot it competes with is " +
                "never penalised and the plan keeps routing wheat through a chest",
        )
    }

    /**
     * MCO-495 — obsidian's only routes were finding it and breaking an *ender chest*, which
     * drops eight and is itself made of obsidian. Pouring water on still lava is what a player
     * actually does, and it is the same shape as the concrete mechanic above: the bucket comes
     * back empty, so it is the input and the lava — the world's — is not.
     */
    @Test
    fun `obsidian is poured from a water bucket onto lava`() {
        val obsidian = producing("minecraft:obsidian").single()

        assertEquals(SourceType.MechanicTypes.IN_WORLD_TRANSFORM, obsidian.type)
        assertEquals("synthetic/obsidian.json", obsidian.filename)
        assertEquals(
            listOf("minecraft:water_bucket"),
            obsidian.requiredItems.map { it.first.id },
            "the bucket is the input; the lava is the world's and costs nothing",
        )
    }

    /**
     * Cobblestone, stone and basalt form the same way in principle and are deliberately left
     * out — for each you either build a farm (a project, already covered by farm supply) or
     * just mine the ordinary block. MCO-495's acceptance criteria say so explicitly, so this
     * pins the boundary rather than trusting it to stay put.
     */
    @Test
    fun `no in-world source is invented for the other lava-formed blocks`() {
        listOf("minecraft:cobblestone", "minecraft:stone", "minecraft:basalt").forEach { id ->
            assertTrue(
                producing(id).isEmpty(),
                "$id must keep its ordinary mined/farmed route, not gain a synthetic one",
            )
        }
    }

    @Test
    fun `entries naming an item the version lacks are dropped`() {
        val withoutCherry = allIds - "minecraft:stripped_cherry_log"
        val sources = SyntheticSources.all(withoutCherry)

        assertTrue(
            sources.none { s -> s.producedItems.any { it.first.id == "minecraft:stripped_cherry_log" } },
            "a version without stripped cherry logs must not gain one"
        )
        assertTrue(
            sources.any { s -> s.producedItems.any { it.first.id == "minecraft:stripped_oak_log" } },
            "unrelated entries must survive the filter"
        )
    }

    @Test
    fun `an entry is dropped when its required input is missing, not just its output`() {
        val withoutDirt = allIds - "minecraft:dirt"
        val sources = SyntheticSources.all(withoutDirt)

        assertTrue(
            sources.none { s -> s.producedItems.any { it.first.id == "minecraft:mud" } },
            "mud must not be producible from an ingredient the version lacks"
        )
    }

    @Test
    fun `an empty registry yields no sources`() {
        assertTrue(SyntheticSources.all(emptySet()).isEmpty())
    }
}
