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
