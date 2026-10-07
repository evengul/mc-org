package app.mcorg.nbt.util

import app.mcorg.nbt.io.BinaryNbtDeserializer
import app.mcorg.nbt.io.CompressionType
import app.mcorg.pipeline.Result
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The parse is blocking, so a cancelled request can only stop it through its thread: mc-web runs
 * it under `runInterruptible`, which interrupts the thread on cancellation or timeout (MCO-426).
 * These pin that each phase of the parse notices.
 *
 * The flag is set before the call rather than from another thread mid-parse, because a test that
 * races a parser this fast proves nothing either way. Each phase is reached on its own, so a check
 * removed from one of them fails the test for that phase.
 */
class ParseInterruptionTest {

    @AfterEach
    fun clearInterrupt() {
        Thread.interrupted()
    }

    private fun nbt(block: DataOutputStream.() -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { it.block() }
        return out.toByteArray()
    }

    private val schematic = nbt {
        writeByte(10); writeUTF("")
        writeByte(10); writeUTF("Metadata")
        writeByte(8); writeUTF("Name"); writeUTF("interrupted")
        writeByte(0)
        writeByte(10); writeUTF("Regions")
        writeByte(10); writeUTF("main")
        writeByte(10); writeUTF("Size")
        writeByte(3); writeUTF("x"); writeInt(64)
        writeByte(3); writeUTF("y"); writeInt(64)
        writeByte(3); writeUTF("z"); writeInt(64)
        writeByte(0)
        writeByte(9); writeUTF("BlockStatePalette")
        writeByte(10); writeInt(2)
        writeByte(8); writeUTF("Name"); writeUTF("minecraft:air"); writeByte(0)
        writeByte(8); writeUTF("Name"); writeUTF("minecraft:stone"); writeByte(0)
        writeByte(12); writeUTF("BlockStates"); writeInt(8192)
        repeat(8192) { writeLong(0x5555_5555_5555_5555L) }
        writeByte(0)
        writeByte(0)
        writeByte(0)
    }

    @Test
    fun `the schematic parses when nobody interrupts it`() {
        assertTrue(LitematicaReader.readLitematica(schematic) is Result.Success<*>)
    }

    @Test
    fun `an interrupted thread stops reading the file`() {
        Thread.currentThread().interrupt()
        assertFailsWith<InterruptedException> { LitematicaReader.readLitematica(schematic) }
        assertFalse(Thread.currentThread().isInterrupted, "the flag is consumed by the throw, as for any InterruptedException")
    }

    @Test
    fun `an interrupted thread stops reading a compound`() {
        // Compounds only — no list or array whose own check would notice first.
        val compound = nbt {
            writeByte(10); writeUTF("")
            repeat(1_000) { writeByte(3); writeUTF("k$it"); writeInt(it) }
            writeByte(0)
        }
        Thread.currentThread().interrupt()
        assertFailsWith<InterruptedException> {
            BinaryNbtDeserializer<Any>(CompressionType.NONE).fromBytes(compound)
        }
    }

    @Test
    fun `an interrupted thread stops reading a list`() {
        val list = nbt {
            writeByte(9); writeUTF("")
            writeByte(3); writeInt(1_000)
            repeat(1_000) { writeInt(it) }
        }
        Thread.currentThread().interrupt()
        assertFailsWith<InterruptedException> {
            BinaryNbtDeserializer<Any>(CompressionType.NONE).fromBytes(list)
        }
    }

    @Test
    fun `an interrupted thread stops reading a packed array`() {
        // A root that is nothing but a long array: no compound loop to notice the flag first, and
        // the read happens inside tryRead, which turns every other exception into a failure.
        val array = nbt {
            writeByte(12); writeUTF("")
            writeInt(100_000)
            repeat(100_000) { writeLong(it.toLong()) }
        }
        Thread.currentThread().interrupt()
        assertFailsWith<InterruptedException> {
            BinaryNbtDeserializer<Any>(CompressionType.NONE).fromBytes(array)
        }
    }

    @Test
    fun `an interrupted thread stops decoding block states`() {
        // Decoding runs after the tree is built and is where a large region spends its time, so
        // it has to check on its own: the deserializer's checks are all behind it by then.
        val root = (BinaryNbtDeserializer<Any>(CompressionType.NONE).fromBytes(schematic) as Result.Success).value.tag.value
        Thread.currentThread().interrupt()
        assertFailsWith<InterruptedException> { LitematicaReader.fromRoot(root) }
    }
}
