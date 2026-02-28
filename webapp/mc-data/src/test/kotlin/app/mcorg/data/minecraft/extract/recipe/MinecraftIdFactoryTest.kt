package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MinecraftIdFactoryTest {

    @Test
    fun `creates Item for regular id`() {
        val result = MinecraftIdFactory.minecraftIdFromId("minecraft:stone")
        assertIs<Item>(result)
        assertEquals("minecraft:stone", result.id)
        assertEquals("", result.name)
    }

    @Test
    fun `creates MinecraftTag for hash-prefixed id`() {
        val result = MinecraftIdFactory.minecraftIdFromId("#minecraft:logs")
        assertIs<MinecraftTag>(result)
        assertEquals("#minecraft:logs", result.id)
        assertEquals("", result.name)
        assertEquals(emptyList(), result.content)
    }

    @Test
    fun `creates Item for id without minecraft prefix`() {
        val result = MinecraftIdFactory.minecraftIdFromId("stone")
        assertIs<Item>(result)
        assertEquals("stone", result.id)
    }
}
