package app.mcorg.nbt.io

import app.mcorg.nbt.tag.*
import app.mcorg.pipeline.TestUtils.assertResultSuccess
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BigEndianNbtInputStreamTest {
    private fun createInput(block: DataOutputStream.() -> Unit): BigEndianNbtInputStream {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { it.block() }
        return BigEndianNbtInputStream(ByteArrayInputStream(baos.toByteArray()))
    }

    @Test
    fun readByteTag() {
        val input = createInput { writeByte(42) }
        val tag = assertResultSuccess(input.readByteTag())
        assertEquals(42.toByte(), (tag as ByteTag).value)
    }

    @Test
    fun readShortTag() {
        val input = createInput { writeShort(1234) }
        val tag = assertResultSuccess(input.readShortTag())
        assertEquals(1234.toShort(), (tag as ShortTag).value)
    }

    @Test
    fun readIntTag() {
        val input = createInput { writeInt(100000) }
        val tag = assertResultSuccess(input.readIntTag())
        assertEquals(100000, (tag as IntTag).value)
    }

    @Test
    fun readLongTag() {
        val input = createInput { writeLong(9876543210L) }
        val tag = assertResultSuccess(input.readLongTag())
        assertEquals(9876543210L, (tag as LongTag).value)
    }

    @Test
    fun readFloatTag() {
        val input = createInput { writeFloat(3.14f) }
        val tag = assertResultSuccess(input.readFloatTag())
        assertEquals(3.14f, (tag as FloatTag).value)
    }

    @Test
    fun readDoubleTag() {
        val input = createInput { writeDouble(2.718281828) }
        val tag = assertResultSuccess(input.readDoubleTag())
        assertEquals(2.718281828, (tag as DoubleTag).value)
    }

    @Test
    fun readStringTag() {
        val input = createInput { writeUTF("hello world") }
        val tag = assertResultSuccess(input.readStringTag())
        assertEquals("hello world", (tag as StringTag).value)
    }

    @Test
    fun readByteListTag() {
        val input = createInput {
            writeInt(3)
            writeByte(10)
            writeByte(20)
            writeByte(30)
        }
        val tag = assertResultSuccess(input.readByteListTag())
        assertContentEquals(byteArrayOf(10, 20, 30), (tag as ByteListTag).value)
    }

    @Test
    fun readCompoundTag() {
        val input = createInput {
            writeByte(IntTag.ID.toInt())  // tag type: Int
            writeUTF("myInt")             // key
            writeInt(42)                  // value
            writeByte(0)                  // end tag
        }
        val tag = assertResultSuccess(input.readCompoundTag(512))
        assertEquals(IntTag(42), tag.value["myInt"])
    }

    // The array readers grow their buffer as elements arrive rather than allocating the declared
    // length up front (MCO-426), so a length past the first buffer exercises every growth step.
    private val largeLength = 100_003

    @Test
    fun `a large byte array is read in full`() {
        val expected = ByteArray(largeLength) { it.toByte() }
        val input = createInput {
            writeInt(largeLength)
            write(expected)
        }
        val tag = assertResultSuccess(input.readByteListTag())
        assertContentEquals(expected, (tag as ByteListTag).value)
    }

    @Test
    fun `a large int array is read in full`() {
        val expected = IntArray(largeLength) { it * 31 }
        val input = createInput {
            writeInt(largeLength)
            expected.forEach { writeInt(it) }
        }
        val tag = assertResultSuccess(input.readIntListTag())
        assertContentEquals(expected, (tag as IntListTag).value)
    }

    @Test
    fun `a large long array is read in full`() {
        val expected = LongArray(largeLength) { it * 0x1_0000_0001L }
        val input = createInput {
            writeInt(largeLength)
            expected.forEach { writeLong(it) }
        }
        val tag = assertResultSuccess(input.readLongListTag())
        assertContentEquals(expected, (tag as LongListTag).value)
    }

    @Test
    fun `an empty array is read as empty`() {
        val input = createInput { writeInt(0) }
        val tag = assertResultSuccess(input.readLongListTag())
        assertEquals(0, (tag as LongListTag).value.size)
    }
}
