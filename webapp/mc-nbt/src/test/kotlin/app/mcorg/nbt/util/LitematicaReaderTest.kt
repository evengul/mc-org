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
    fun `excludes air blocks from the material list`() {
        // The 8x8x2 region is mostly empty cells; air must never be a material.
        val litematica = TestUtils.assertResultSuccess(LitematicaReader.readLitematica(getFileAsStream()))

        assertEquals(null, litematica.items["minecraft:air"])
        assertEquals(null, litematica.items["minecraft:cave_air"])
        assertEquals(null, litematica.items["minecraft:void_air"])
        assertTrue(litematica.items.isNotEmpty())
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

    // ---- subregions (MCO-398) ---------------------------------------------------------

    /** A `BlockStatePalette` entry list: `[{Name: air}, {Name: <block>}]`, so index 1 is the block. */
    private fun DataOutputStream.writePaletteEntry(block: String) {
        writeByte(9)                 // ListTag
        writeUTF("BlockStatePalette")
        writeByte(10)                // of CompoundTag
        writeInt(2)
        writeStringEntry("Name", "minecraft:air")
        writeByte(0)
        writeStringEntry("Name", block)
        writeByte(0)
    }

    /**
     * One 1x1x1 region holding a single [block].
     *
     * A two-entry palette packs at 2 bits per block, so one block state of value `1` selects
     * palette index 1 — the block rather than the air that pads index 0.
     */
    private fun DataOutputStream.writeSingleBlockRegion(name: String, block: String) {
        writeCompoundEntry(name) {
            writeCompoundEntry("Size") {
                writeIntEntry("x", 1)
                writeIntEntry("y", 1)
                writeIntEntry("z", 1)
            }
            writePaletteEntry(block)
            writeByte(12)            // LongListTag
            writeUTF("BlockStates")
            writeInt(1)
            writeLong(1L)
        }
    }

    /**
     * A one-block region whose block is also a container holding [stocked] (MCO-322).
     *
     * Litematica writes container contents as a `TileEntities` list of compounds, each with an
     * `Items` list of `{id, Count}`. That is the shape being asserted against, so it is written
     * by hand here rather than borrowed from a fixture.
     */
    private fun DataOutputStream.writeStockedRegion(
        name: String,
        block: String,
        stocked: List<Pair<String, Int>>,
    ) {
        writeCompoundEntry(name) {
            writeCompoundEntry("Size") {
                writeIntEntry("x", 1)
                writeIntEntry("y", 1)
                writeIntEntry("z", 1)
            }
            writePaletteEntry(block)
            writeByte(12)            // LongListTag
            writeUTF("BlockStates")
            writeInt(1)
            writeLong(1L)
            // TileEntities: ListTag of CompoundTag
            writeByte(9)
            writeUTF("TileEntities")
            writeByte(10)
            writeInt(1)
            // one tile entity, holding an Items list
            writeByte(9)
            writeUTF("Items")
            writeByte(10)
            writeInt(stocked.size)
            stocked.forEach { (id, count) ->
                writeStringEntry("id", id)
                writeByte(1)         // ByteTag
                writeUTF("Count")
                writeByte(count)
                writeByte(0)         // end of this item compound
            }
            writeByte(0)             // end of the tile-entity compound
        }
    }

    private fun stockedFile() = buildRootCompound {
        writeCompoundEntry("Metadata") {
            writeStringEntry("Name", "Stocked sorter")
            writeStringEntry("Author", "tester")
            writeCompoundEntry("EnclosingSize") {
                writeIntEntry("x", 1)
                writeIntEntry("y", 1)
                writeIntEntry("z", 1)
            }
        }
        writeCompoundEntry("Regions") {
            writeStockedRegion(
                "Sorter",
                "minecraft:hopper",
                listOf("minecraft:redstone" to 32, "minecraft:hopper" to 4),
            )
        }
    }

    private fun twoRegionFile() = buildRootCompound {
        writeCompoundEntry("Metadata") {
            writeStringEntry("Name", "Two parter")
            writeStringEntry("Author", "tester")
            writeCompoundEntry("EnclosingSize") {
                writeIntEntry("x", 1)
                writeIntEntry("y", 1)
                writeIntEntry("z", 1)
            }
        }
        writeCompoundEntry("Regions") {
            writeSingleBlockRegion("Functional frame", "minecraft:oak_planks")
            writeSingleBlockRegion("Display glass", "minecraft:glass")
        }
    }

    @Test
    fun `subregions are kept with their names`() {
        val lit = TestUtils.assertResultSuccess(LitematicaReader.readLitematica(twoRegionFile()))

        assertEquals(listOf("Functional frame", "Display glass"), lit.regions.map { it.name })
        assertEquals(mapOf("minecraft:oak_planks" to 1), lit.regions[0].items)
        assertEquals(mapOf("minecraft:glass" to 1), lit.regions[1].items)
    }

    @Test
    fun `items stays the flattening of the regions`() {
        // The documented invariant on Litematica. Callers that only want "what does this cost"
        // must keep working untouched, which is the whole reason both are carried.
        val lit = TestUtils.assertResultSuccess(LitematicaReader.readLitematica(twoRegionFile()))

        val flattened = lit.regions
            .flatMap { it.items.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, counts) -> counts.sum() }
        assertEquals(flattened, lit.items)
    }

    @Test
    fun `a real single-region file reports its one region`() {
        // Litematica names a lone region after the schematic itself — which is exactly why the
        // review screen renders no section header for it.
        val lit = TestUtils.assertResultSuccess(
            LitematicaReader.readLitematica(getFileAsStream("litematica/WiskeProSorter.litematic"))
        )

        assertEquals(listOf("WiskeProSorter"), lit.regions.map { it.name })
        assertEquals(lit.items, lit.regions.single().items)
    }

    @Test
    fun `a region named Unnamed is carried as-is`() {
        // The other thing real files do. Naming it is the review screen's problem, not the
        // reader's — it reports what the file says.
        val lit = TestUtils.assertResultSuccess(
            LitematicaReader.readLitematica(getFileAsStream("litematica/Dig_Sort_III.litematic"))
        )

        assertEquals(listOf("Unnamed"), lit.regions.map { it.name })
    }

    @Test
    fun `a region with no materials is not reported`() {
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
            writeCompoundEntry("Regions") {
                writeSingleBlockRegion("Real", "minecraft:oak_planks")
                writeCompoundEntry("Empty") {}
            }
        }

        val lit = TestUtils.assertResultSuccess(LitematicaReader.readLitematica(bytes))

        assertEquals(listOf("Real"), lit.regions.map { it.name })
    }

    private val defaultFile = "litematica/Compact_AB_Tilable_2x_Shulker_Loader.litematic"

    fun getFileAsStream(filePath: String = defaultFile) =
        this::class.java.classLoader.getResourceAsStream(filePath)!!


    // --- container contents vs placed structure (MCO-322) ---

    @Test
    fun `container contents are reported apart from the blocks they sit in`() {
        val lit = TestUtils.assertResultSuccess(LitematicaReader.readLitematica(stockedFile()))

        assertEquals(mapOf("minecraft:redstone" to 32, "minecraft:hopper" to 4), lit.containerItems)
    }

    @Test
    fun `container contents stay part of the total`() {
        // The split marks rows; it does not remove them. A stocked container is normally part
        // of the build — filter items, fuel, what a farm consumes — so the material list still
        // means "everything this costs".
        val lit = TestUtils.assertResultSuccess(LitematicaReader.readLitematica(stockedFile()))

        // One hopper placed plus four in the container, and the stocked redstone.
        assertEquals(5, lit.items["minecraft:hopper"])
        assertEquals(32, lit.items["minecraft:redstone"])
    }

    @Test
    fun `a region carries its own container contents`() {
        val lit = TestUtils.assertResultSuccess(LitematicaReader.readLitematica(stockedFile()))

        assertEquals(
            mapOf("minecraft:redstone" to 32, "minecraft:hopper" to 4),
            lit.regions.single().containerItems,
        )
    }

    @Test
    fun `a schematic with empty containers reports none`() {
        val lit = TestUtils.assertResultSuccess(LitematicaReader.readLitematica(twoRegionFile()))

        assertTrue(lit.containerItems.isEmpty())
        assertTrue(lit.regions.all { it.containerItems.isEmpty() })
    }

    @Test
    fun `a real stocked schematic reports what it is loaded with`() {
        // Compact_AB_Tilable_2x_Shulker_Loader is 96% container contents — 3,165 redstone and
        // 125 shulker boxes. That is not clutter someone forgot to clear: it is what a shulker
        // loader loads, and it is exactly the case the review screen needs to be able to name.
        val lit = TestUtils.assertResultSuccess(
            LitematicaReader.readLitematica(
                getFileAsStream("litematica/Compact_AB_Tilable_2x_Shulker_Loader.litematic")
            )
        )

        assertEquals(3165, lit.containerItems["minecraft:redstone"])
        assertEquals(125, lit.containerItems["minecraft:shulker_box"])
        // A subset of the total, never a separate list.
        lit.containerItems.forEach { (id, stocked) ->
            assertTrue(
                lit.items.getValue(id) >= stocked,
                "$id: container count $stocked must not exceed the total ${lit.items[id]}",
            )
        }
    }

    @Test
    fun `a real schematic with nothing stored reports no container contents`() {
        val lit = TestUtils.assertResultSuccess(
            LitematicaReader.readLitematica(getFileAsStream("litematica/WiskeProSorter.litematic"))
        )

        assertTrue(lit.containerItems.isEmpty())
    }

}
