package app.mcorg.domain.model.resources

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProductionPathTest {

    @Test
    fun `encode leaf item with no source`() {
        val path = ProductionPath(itemId = "minecraft:diamond", source = null)
        assertEquals("minecraft:diamond", path.encode())
    }

    @Test
    fun `encode simple crafting with no requirements shown`() {
        val path = ProductionPath(
            itemId = "minecraft:stick",
            source = "minecraft:crafting_shapeless"
        )
        assertEquals("minecraft:stick>minecraft:crafting_shapeless", path.encode())
    }

    @Test
    fun `encode crafting with single requirement`() {
        val path = ProductionPath(
            itemId = "minecraft:blue_bed",
            source = "minecraft:crafting_shaped",
            requirements = listOf(
                ProductionPath(itemId = "minecraft:blue_wool", source = "minecraft:shearing")
            )
        )
        assertEquals(
            "minecraft:blue_bed>minecraft:crafting_shaped~minecraft:blue_wool>minecraft:shearing",
            path.encode()
        )
    }

    @Test
    fun `encode crafting with multiple requirements`() {
        val path = ProductionPath(
            itemId = "minecraft:blue_bed",
            source = "minecraft:crafting_shaped",
            requirements = listOf(
                ProductionPath(itemId = "minecraft:blue_wool", source = "minecraft:shearing"),
                ProductionPath(itemId = "minecraft:oak_planks", source = "minecraft:crafting_shapeless")
            )
        )
        assertEquals(
            "minecraft:blue_bed>minecraft:crafting_shaped~minecraft:blue_wool>minecraft:shearing|minecraft:oak_planks>minecraft:crafting_shapeless",
            path.encode()
        )
    }

    @Test
    fun `encode deep nested path`() {
        val path = ProductionPath(
            itemId = "minecraft:diamond_sword",
            source = "minecraft:crafting_shaped",
            requirements = listOf(
                ProductionPath(itemId = "minecraft:diamond", source = "minecraft:mining"),
                ProductionPath(
                    itemId = "minecraft:stick",
                    source = "minecraft:crafting_shapeless",
                    requirements = listOf(
                        ProductionPath(
                            itemId = "minecraft:oak_planks",
                            source = "minecraft:crafting_shapeless",
                            requirements = listOf(
                                ProductionPath(itemId = "minecraft:oak_log", source = "minecraft:mining")
                            )
                        )
                    )
                )
            )
        )

        val encoded = path.encode()
        assertTrue(encoded.contains("minecraft:diamond_sword>minecraft:crafting_shaped"))
        assertTrue(encoded.contains("minecraft:diamond>minecraft:mining"))
        assertTrue(encoded.contains("minecraft:oak_log>minecraft:mining"))
    }

    @Test
    fun `decode leaf item`() {
        val path = ProductionPath.decode("minecraft:diamond")
        assertNotNull(path)
        assertEquals("minecraft:diamond", path!!.itemId)
        assertNull(path.source)
        assertTrue(path.requirements.isEmpty())
    }

    @Test
    fun `decode simple crafting`() {
        val path = ProductionPath.decode("minecraft:stick>minecraft:crafting_shapeless")
        assertNotNull(path)
        assertEquals("minecraft:stick", path!!.itemId)
        assertEquals("minecraft:crafting_shapeless", path.source)
        assertTrue(path.requirements.isEmpty())
    }

    @Test
    fun `decode crafting with single requirement`() {
        val encoded = "minecraft:blue_bed>minecraft:crafting_shaped~minecraft:blue_wool>minecraft:shearing"
        val path = ProductionPath.decode(encoded)

        assertNotNull(path)
        assertEquals("minecraft:blue_bed", path!!.itemId)
        assertEquals("minecraft:crafting_shaped", path.source)
        assertEquals(1, path.requirements.size)
        assertEquals("minecraft:blue_wool", path.requirements[0].itemId)
        assertEquals("minecraft:shearing", path.requirements[0].source)
    }

    @Test
    fun `decode crafting with multiple requirements`() {
        val encoded = "minecraft:blue_bed>minecraft:crafting_shaped~minecraft:blue_wool>minecraft:shearing|minecraft:oak_planks>minecraft:crafting_shapeless"
        val path = ProductionPath.decode(encoded)

        assertNotNull(path)
        assertEquals("minecraft:blue_bed", path!!.itemId)
        assertEquals(2, path.requirements.size)
        assertEquals("minecraft:blue_wool", path.requirements[0].itemId)
        assertEquals("minecraft:oak_planks", path.requirements[1].itemId)
    }

    @Test
    fun `roundtrip encode and decode`() {
        val original = ProductionPath(
            itemId = "minecraft:blue_bed",
            source = "minecraft:crafting_shaped",
            requirements = listOf(
                ProductionPath(itemId = "minecraft:blue_wool", source = "minecraft:shearing"),
                ProductionPath(itemId = "minecraft:oak_planks", source = "minecraft:crafting_shapeless")
            )
        )

        val encoded = original.encode()
        val decoded = ProductionPath.decode(encoded)

        assertNotNull(decoded)
        assertEquals(original.itemId, decoded!!.itemId)
        assertEquals(original.source, decoded.source)
        assertEquals(original.requirements.size, decoded.requirements.size)
    }

    @Test
    fun `decode invalid string returns null`() {
        assertNull(ProductionPath.decode(""))
        assertNull(ProductionPath.decode("   "))
    }

    @Test
    fun `getAllItemIds returns all unique items`() {
        val path = ProductionPath(
            itemId = "minecraft:blue_bed",
            source = "minecraft:crafting_shaped",
            requirements = listOf(
                ProductionPath(itemId = "minecraft:blue_wool", source = "minecraft:shearing"),
                ProductionPath(itemId = "minecraft:oak_planks", source = "minecraft:crafting_shapeless")
            )
        )

        val items = path.getAllItemIds()
        assertEquals(3, items.size)
        assertTrue(items.contains("minecraft:blue_bed"))
        assertTrue(items.contains("minecraft:blue_wool"))
        assertTrue(items.contains("minecraft:oak_planks"))
    }

    @Test
    fun `countDecisions counts all nodes with sources`() {
        val path = ProductionPath(
            itemId = "minecraft:blue_bed",
            source = "minecraft:crafting_shaped",
            requirements = listOf(
                ProductionPath(itemId = "minecraft:blue_wool", source = "minecraft:shearing"),
                ProductionPath(itemId = "minecraft:oak_planks", source = null) // Leaf
            )
        )

        assertEquals(2, path.countDecisions()) // blue_bed + blue_wool
    }

    @Test
    fun `isComplete returns true for leaf item`() {
        val path = ProductionPath(itemId = "minecraft:diamond", source = null)
        assertTrue(path.isComplete())
    }

    @Test
    fun `isComplete returns false when requirements missing`() {
        val path = ProductionPath(
            itemId = "minecraft:blue_bed",
            source = "minecraft:crafting_shaped",
            requirements = emptyList() // Has source but no requirements selected
        )
        assertFalse(path.isComplete())
    }

    @Test
    fun `isComplete returns true when all paths lead to leaves`() {
        val path = ProductionPath(
            itemId = "minecraft:blue_bed",
            source = "minecraft:crafting_shaped",
            requirements = listOf(
                ProductionPath(itemId = "minecraft:blue_wool", source = null),
                ProductionPath(itemId = "minecraft:oak_planks", source = null)
            )
        )
        assertTrue(path.isComplete())
    }

    @Test
    fun `encoded path stays under 2000 characters for complex tree`() {
        // Simulate a reasonably complex path
        val path = ProductionPath(
            itemId = "minecraft:diamond_sword",
            source = "minecraft:crafting_shaped",
            requirements = listOf(
                ProductionPath(itemId = "minecraft:diamond", source = "minecraft:mining"),
                ProductionPath(
                    itemId = "minecraft:stick",
                    source = "minecraft:crafting_shapeless",
                    requirements = listOf(
                        ProductionPath(
                            itemId = "minecraft:oak_planks",
                            source = "minecraft:crafting_shapeless",
                            requirements = listOf(
                                ProductionPath(itemId = "minecraft:oak_log", source = "minecraft:mining")
                            )
                        )
                    )
                )
            )
        )

        val encoded = path.encode()
        assertTrue(encoded.length < 2000, "Encoded path is ${encoded.length} characters")
    }
}

