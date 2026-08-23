package app.mcorg.nbt.io

import app.mcorg.domain.model.minecraft.Litematica
import app.mcorg.nbt.failure.NBTFailure
import app.mcorg.nbt.util.LitematicaReader
import app.mcorg.pipeline.Result
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.ThrowingSupplier
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.time.Duration
import java.util.zip.GZIPOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The four denial-of-service shapes from MCO-345, pinned so the guards cannot be removed quietly.
 *
 * Every case asserts *bounded time* as well as failure. A parser that eventually returns an error
 * after two billion no-op iterations has not been fixed — the point of these guards is that the
 * refusal is cheap, because the attack is.
 */
class HostileNbtInputTest {

    /** Generous next to the microseconds these should take, tight next to the minutes they used to. */
    private val bounded: Duration = Duration.ofSeconds(10)

    private fun nbt(block: DataOutputStream.() -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { it.block() }
        return out.toByteArray()
    }

    /** Parses on a separate thread so a runaway loop is cut off rather than waited out. */
    private fun parseWithin(bytes: ByteArray, what: String): Result<NBTFailure, Litematica> =
        assertTimeoutPreemptively(
            bounded,
            ThrowingSupplier { LitematicaReader.readLitematica(bytes) },
            "$what should complete promptly",
        )

    /**
     * Asserts the parse fails *and* names [because] in the failure.
     *
     * The message check is not decoration. Malformed input fails for all sorts of reasons — a
     * bomb of zeroes happens to decode as an empty `TAG_End` root and be rejected as the wrong
     * structure — so a bare "it failed" would pass with every guard in this file deleted.
     */
    private fun assertRefused(bytes: ByteArray, what: String, because: String) {
        val result = parseWithin(bytes, what)
        assertTrue(result is Result.Failure<*>, "$what should be refused, but parsed successfully")
        val message = ((result as Result.Failure<*>).error as NBTFailure.DeserializeError).cause
        assertTrue(
            because in message,
            "$what should be refused by the size guard; expected \"$because\" in: $message",
        )
    }

    @Test
    fun `a TAG_List of TAG_End declaring two billion elements is refused`() {
        // TAG_End's reader consumes no bytes, so each of the declared elements is a free append to
        // an ArrayList. Fifteen bytes of input, 2.1 billion appends, one dead JVM.
        val bomb = nbt {
            writeByte(9)                 // root is a TAG_List
            writeUTF("")
            writeByte(0)                 // of TAG_End
            writeInt(Int.MAX_VALUE)
        }

        assertTrue(bomb.size < 32, "the bomb is supposed to be tiny; it is ${bomb.size} bytes")
        assertRefused(bomb, "a TAG_End list bomb", because = "TAG_End consumes no bytes")
    }

    @Test
    fun `a TAG_Byte_Array declaring Int MAX_VALUE bytes is refused without allocating`() {
        val bomb = nbt {
            writeByte(7)                 // root is a TAG_Byte_Array
            writeUTF("")
            writeInt(Int.MAX_VALUE)      // ...of 2 GB, from nine bytes of input
        }

        assertRefused(bomb, "a 2 GB byte-array claim", because = "TAG_Byte_Array declared 2147483647 elements")
    }

    @Test
    fun `a TAG_Long_Array declaring more longs than the stream can hold is refused`() {
        val bomb = nbt {
            writeByte(12)
            writeUTF("")
            writeInt(Int.MAX_VALUE / 4)  // x8 bytes each = 4 GB
        }

        assertRefused(bomb, "an oversized long-array claim", because = "only")
    }

    @Test
    fun `a gzip bomb is refused at the decompressed limit`() {
        // The payload has to be *well-formed* NBT that legitimately runs past the cap, otherwise
        // the parser rejects it as malformed long before the bound matters and the test proves
        // nothing. So: a compound of 1 MB byte arrays, each one honestly supplied. It sails
        // through every per-allocation check and is stopped only by the stream's own ceiling.
        val oneMegabyte = 1 shl 20
        val payload = nbt {
            writeByte(10)                                   // root compound
            writeUTF("")
            repeat((NbtLimits.MAX_DECOMPRESSED_BYTES / oneMegabyte).toInt() + 4) { index ->
                writeByte(7)                                // TAG_Byte_Array
                writeUTF("chunk$index")
                writeInt(oneMegabyte)
                write(ByteArray(oneMegabyte))
            }
            writeByte(0)
        }

        val raw = ByteArrayOutputStream()
        GZIPOutputStream(raw).use { it.write(payload) }
        val bomb = raw.toByteArray()

        assertTrue(
            payload.size > NbtLimits.MAX_DECOMPRESSED_BYTES,
            "the payload must exceed the cap to exercise it; it is ${payload.size} bytes",
        )
        assertTrue(
            bomb.size < 512 * 1024,
            "the bomb should be small on the wire; it is ${bomb.size} bytes",
        )

        // Refused as the budget runs out, one chunk short of the cap — the allocation guard gets
        // there before the stream cap does, which is the cheaper of the two and the one we want
        // firing. Either way the bomb never inflates past 16 MB.
        assertRefused(bomb, "a gzip bomb", because = "remain")
    }

    @Test
    fun `the decompressed cap stops a stream that never declares a length`() {
        // Direct cover for BoundedInputStream: the allocation guard above can only check reads
        // that announce their size. Fixed-width reads — every primitive tag, every compound key —
        // announce nothing, so the stream itself has to be the backstop.
        val stream = BoundedInputStream(ByteArray(64).inputStream(), limit = 16)

        assertTrue(stream.read(ByteArray(16)) == 16, "reads up to the limit should succeed")
        assertEquals(0L, stream.remaining())
        assertFailsWith<NbtSizeLimitExceeded> { stream.read(ByteArray(16)) }
    }

    @Test
    fun `a TAG_List of empty compounds is refused on the heap budget, not the byte budget`() {
        // The hole the wire-byte check did not cover, and the reason MAX_LIST_HEAP_BYTES exists.
        //
        // An empty compound is one wire byte — its terminating TAG_End — so 16 M of them fit
        // inside MAX_DECOMPRESSED_BYTES and every existing guard waves them through. Each one then
        // allocates a CompoundTag, a LinkedHashMap and a list slot: ~80 bytes of heap per byte of
        // input. Measured before the fix, against the production -Xmx768m: OutOfMemoryError in
        // 1.7s from a 15.5 kB gzip.
        //
        // Note this file must stay gzipped and the assertion on its size must stay. Uncompressed
        // it is 16 MB, which the route-level upload cap would refuse first — and then this test
        // would be proving the wrong layer.
        val elements = 16_000_000
        val payload = nbt {
            writeByte(9)                 // root is a TAG_List
            writeUTF("")
            writeByte(10)                // of TAG_Compound
            writeInt(elements)
            repeat(elements) { writeByte(0) }   // each: immediately terminated, one byte
        }

        val raw = ByteArrayOutputStream()
        GZIPOutputStream(raw).use { it.write(payload) }
        val bomb = raw.toByteArray()

        assertTrue(
            payload.size < NbtLimits.MAX_DECOMPRESSED_BYTES,
            "the payload must stay *under* the byte cap or it proves the wrong guard; " +
                "it is ${payload.size} bytes",
        )
        assertTrue(bomb.size < 64 * 1024, "the bomb should be tiny on the wire; it is ${bomb.size} bytes")

        assertRefused(bomb, "a compound-list heap bomb", because = "list heap budget")
    }

    @Test
    fun `a compound full of unknown tags does not accumulate an unbounded error string`() {
        // readCompoundTag records a failure per malformed child and keeps going, and
        // LitematicaReader renders the result into a message. An unknown tag id costs three wire
        // bytes, so the 16 MB budget bought ~5.5 M accumulated failures and a 143 MB String —
        // measured — for a message that is then discarded.
        //
        // Asserts the *rendered length*, because that is the thing that allocated. Capping the
        // accumulation without bounding toString, or vice versa, still fails this.
        val unknownTags = 500_000
        val bytes = nbt {
            writeByte(10)                // root compound
            writeUTF("")
            repeat(unknownTags) {
                writeByte(100)           // no such tag type
                writeUTF("")
            }
            writeByte(0)
        }

        val result = parseWithin(bytes, "a compound of unknown tags")
        assertTrue(result is Result.Failure<*>, "unknown tag types should fail the parse")

        val message = ((result as Result.Failure<*>).error as NBTFailure.DeserializeError).cause
        assertTrue(
            message.length < 4096,
            "the failure message should stay small; it is ${message.length} chars",
        )
    }

    @Test
    fun `a region declaring two billion blocks reads only what its data addresses`() {
        // Size is attacker-declared and was iterated verbatim. 2000 x 2000 x 500 stays under
        // Int.MAX_VALUE, so it slipped past the only guard that existed, and the loop body's
        // `return@repeat` continued rather than broke.
        val bytes = nbt {
            writeByte(10)
            writeUTF("")
            writeByte(10); writeUTF("Metadata")
            writeByte(8); writeUTF("Name"); writeUTF("bomb")
            writeByte(8); writeUTF("Author"); writeUTF("attacker")
            writeByte(0)
            writeByte(10); writeUTF("Regions")
            writeByte(10); writeUTF("huge")
            writeByte(10); writeUTF("Size")
            writeByte(3); writeUTF("x"); writeInt(2000)
            writeByte(3); writeUTF("y"); writeInt(2000)
            writeByte(3); writeUTF("z"); writeInt(500)
            writeByte(0)
            writeByte(9); writeUTF("BlockStatePalette")
            writeByte(10); writeInt(2)
            writeByte(8); writeUTF("Name"); writeUTF("minecraft:air"); writeByte(0)
            writeByte(8); writeUTF("Name"); writeUTF("minecraft:stone"); writeByte(0)
            writeByte(12); writeUTF("BlockStates"); writeInt(1); writeLong(1L)
            writeByte(0)
            writeByte(0)
            writeByte(0)
        }

        assertTrue(bytes.size < 300, "the file is supposed to be tiny; it is ${bytes.size} bytes")

        // This one is well-formed, so it parses — the fix is that it parses in bounded time,
        // reading only the 32 blocks its single packed long can actually address.
        val result = parseWithin(bytes, "a 2-billion-block claim")
        assertTrue(result is Result.Success<*>, "a well-formed file should still parse: $result")
    }
}
