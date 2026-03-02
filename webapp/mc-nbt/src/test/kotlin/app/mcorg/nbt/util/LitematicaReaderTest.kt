package app.mcorg.nbt.util

import app.mcorg.nbt.failure.NBTFailure
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.TestUtils
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class LitematicaReaderTest {

    // Helper: build raw NBT bytes with a DataOutputStream
    private fun buildNbtBytes(block: DataOutputStream.() -> Unit): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { it.block() }
        return baos.toByteArray()
    }

    // Helper: build a root CompoundTag with given children written inside the block
    private fun buildRootCompound(block: DataOutputStream.() -> Unit): ByteArray = buildNbtBytes {
        writeByte(10)   // CompoundTag type
        writeUTF("")    // root name
        block()
        writeByte(0)    // end tag
    }

    // Helper: write a CompoundTag entry (type 10) with the given key and children
    private fun DataOutputStream.writeCompoundEntry(key: String, block: DataOutputStream.() -> Unit) {
        writeByte(10)
        writeUTF(key)
        block()
        writeByte(0)
    }

    // Helper: write a StringTag entry (type 8)
    private fun DataOutputStream.writeStringEntry(key: String, value: String) {
        writeByte(8)
        writeUTF(key)
        writeUTF(value)
    }

    // Helper: write an IntTag entry (type 3)
    private fun DataOutputStream.writeIntEntry(key: String, value: Int) {
        writeByte(3)
        writeUTF(key)
        writeInt(value)
    }
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

    @Test
    fun `10x Shulker loader`() {
        val lit = TestUtils.assertResultSuccess(
            LitematicaReader.readLitematica(getFileAsStream("litematica/10x Shulker loader.litematic"))
        )
        assertEquals("10x Shulker loader", lit.name)
        assertEquals("lilpebblez", lit.author)
        assertEquals(8, lit.size.first)
        assertEquals(10, lit.size.second)
        assertEquals(10, lit.size.third)
        assertEquals(20, lit.items["minecraft:sticky_piston"])
        assertEquals(80, lit.items["minecraft:observer"])
    }

    @Test
    fun `Dig Sort III`() {
        val lit = TestUtils.assertResultSuccess(
            LitematicaReader.readLitematica(getFileAsStream("litematica/Dig_Sort_III.litematic"))
        )
        assertEquals("Dig Sorter", lit.name)
        assertEquals("HDanke", lit.author)
        assertEquals(30, lit.size.first)
        assertEquals(29, lit.size.second)
        assertEquals(18, lit.size.third)
        assertEquals(128, lit.items["minecraft:chest"])
        assertEquals(112, lit.items["minecraft:trapped_chest"])
    }

    @Test
    fun `WiskeProSorter`() {
        val lit = TestUtils.assertResultSuccess(
            LitematicaReader.readLitematica(getFileAsStream("litematica/WiskeProSorter.litematic"))
        )
        assertEquals("WiskeProSorter", lit.name)
        assertEquals("lilpebblez", lit.author)
        assertEquals(14, lit.size.first)
        assertEquals(15, lit.size.second)
        assertEquals(31, lit.size.third)
        assertEquals(74, lit.items["minecraft:chest"])
        assertEquals(124, lit.items["minecraft:hopper"])
    }

    // --- Error / edge-case tests ---

    @Test
    fun `empty byte array returns DeserializeError`() {
        val result = LitematicaReader.readLitematica(byteArrayOf())
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is NBTFailure.DeserializeError)
    }

    @Test
    fun `random bytes return DeserializeError`() {
        val result = LitematicaReader.readLitematica(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is NBTFailure.DeserializeError)
    }

    @Test
    fun `valid NBT but missing Metadata returns MissingData`() {
        val bytes = buildRootCompound {
            // Regions present, Metadata missing
            writeCompoundEntry("Regions") {}
        }
        val result = LitematicaReader.readLitematica(bytes)
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is NBTFailure.MissingData)
        assertTrue("Metadata" in (error as NBTFailure.MissingData).fields)
    }

    @Test
    fun `valid NBT but missing Regions returns MissingData`() {
        val bytes = buildRootCompound {
            writeCompoundEntry("Metadata") {
                writeStringEntry("Name", "test")
                writeStringEntry("Author", "tester")
                writeCompoundEntry("EnclosingSize") {
                    writeIntEntry("x", 1)
                    writeIntEntry("y", 1)
                    writeIntEntry("z", 1)
                }
            }
        }
        val result = LitematicaReader.readLitematica(bytes)
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is NBTFailure.MissingData)
        assertTrue("Regions" in (error as NBTFailure.MissingData).fields)
    }

    @Test
    fun `valid NBT with empty Regions returns empty items`() {
        val bytes = buildRootCompound {
            writeCompoundEntry("Metadata") {
                writeStringEntry("Name", "test")
                writeStringEntry("Author", "tester")
                writeCompoundEntry("EnclosingSize") {
                    writeIntEntry("x", 1)
                    writeIntEntry("y", 1)
                    writeIntEntry("z", 1)
                }
            }
            writeCompoundEntry("Regions") {}
        }
        val result = LitematicaReader.readLitematica(bytes)
        val lit = TestUtils.assertResultSuccess(result)
        assertEquals("test", lit.name)
        assertEquals("tester", lit.author)
        assertTrue(lit.items.isEmpty())
    }

    @Test
    fun `metadata with missing fields uses defaults`() {
        val bytes = buildRootCompound {
            // Metadata with no Name, Author, or EnclosingSize
            writeCompoundEntry("Metadata") {}
            writeCompoundEntry("Regions") {}
        }
        val result = LitematicaReader.readLitematica(bytes)
        val lit = TestUtils.assertResultSuccess(result)
        assertEquals("Unnamed", lit.name)
        assertEquals("Unknown", lit.author)
        assertEquals(Triple(0, 0, 0), lit.size)
    }

    private val defaultFile = "litematica/Compact_AB_Tilable_2x_Shulker_Loader.litematic"

    fun getFileAsStream(filePath: String = defaultFile) =
        this::class.java.classLoader.getResourceAsStream(filePath)!!

}
