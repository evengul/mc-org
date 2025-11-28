package app.mcorg.nbt.util

import app.mcorg.test.utils.TestUtils
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class LitematicaReaderTest {
    @Test
    fun happyPath() {
        val litematica = TestUtils.assertResultSuccess(LitematicaReader.readLitematica(getFileAsStream()))

        assertEquals("daisy_pig", litematica.author)
        assertEquals("Unnamed", litematica.name)
        assertEquals("", litematica.description)

        assertEquals(8, litematica.size.first)
        assertEquals(8, litematica.size.second)
        assertEquals(2, litematica.size.third)

        assertEquals(7, litematica.items["minecraft:redstone_wire"])
        assertEquals(127, litematica.items["minecraft:shulker_box"])
    }

    private val defaultFile = "litematica/Compact_AB_Tilable_2x_Shulker_Loader.litematic"

    fun getFileAsStream(filePath: String = defaultFile) =
        this::class.java.classLoader.getResourceAsStream(filePath)!!

}