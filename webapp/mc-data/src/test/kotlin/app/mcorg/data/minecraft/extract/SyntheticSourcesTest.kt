package app.mcorg.data.minecraft.extract

import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyntheticSourcesTest {

    private val sources = SyntheticSources.all()

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
}
