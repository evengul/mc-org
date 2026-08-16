package app.mcorg.nbt.io

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Size ceilings for parsing untrusted NBT (MCO-345).
 *
 * NBT is a self-describing format in which the file declares its own array lengths, so a hostile
 * file can claim `ByteArray(0x7FFFFFFF)` in fifteen bytes. Every allocation the parser makes is
 * therefore cross-checked against how many bytes the stream can still supply, and the stream
 * itself is capped so a gzip bomb cannot expand without bound.
 */
object NbtLimits {
    /**
     * Ceiling on the *decompressed* byte count of one NBT document.
     *
     * Sized against the product, not the format. 16 MB of packed block states is on the order of
     * twenty-five million blocks — far past any real build — while the JVM runs on `-Xmx768m` in a
     * 1 GB Fly VM. The bound has to hold for the worst shape, not the average one: a `TAG_List` of
     * `TAG_Byte` fills an `ArrayList` with one reference per byte, so the limit is really a
     * promise about references as much as bytes. Raise it if a legitimate file is ever refused.
     */
    const val MAX_DECOMPRESSED_BYTES: Long = 16L * 1024 * 1024

    /**
     * Smallest number of bytes a tag of [type] can occupy on the wire, used to bound a declared
     * list length before any element is read.
     *
     * `TAG_End` is deliberately absent: its reader consumes zero bytes, so no length is
     * defensible and [BigEndianNbtInputStream] rejects a non-empty list of them outright.
     */
    fun minimumEncodedSize(type: Byte): Long = when (type.toInt()) {
        1 -> 1L      // Byte
        2 -> 2L      // Short
        3 -> 4L      // Int
        4 -> 8L      // Long
        5 -> 4L      // Float
        6 -> 8L      // Double
        7 -> 4L      // ByteArray  — the length prefix alone
        8 -> 2L      // String     — the UTF length prefix alone
        9 -> 5L      // List       — element type + length prefix
        10 -> 1L     // Compound   — the terminating TAG_End
        11 -> 4L     // IntArray   — the length prefix alone
        12 -> 4L     // LongArray  — the length prefix alone
        else -> 1L
    }
}

/** Raised when a document exceeds [NbtLimits.MAX_DECOMPRESSED_BYTES], or claims more than it can supply. */
class NbtSizeLimitExceeded(message: String) : IOException(message)

/**
 * Caps how many bytes may be pulled from [source] and reports how many remain.
 *
 * Wrapping the *decompressed* stream is what makes the cap meaningful — a compression bomb is
 * small on the wire and only becomes dangerous after inflation. [remaining] additionally lets the
 * parser reject an implausible declared length before allocating for it, so the guard costs one
 * comparison rather than an [OutOfMemoryError].
 */
class BoundedInputStream(
    source: InputStream,
    private val limit: Long = NbtLimits.MAX_DECOMPRESSED_BYTES,
) : FilterInputStream(source) {

    private var consumed: Long = 0

    /** Bytes this stream will still hand out before it starts refusing. */
    fun remaining(): Long = limit - consumed

    private fun charge(count: Int): Int {
        if (count > 0) {
            consumed += count
            if (consumed > limit) {
                throw NbtSizeLimitExceeded("NBT input exceeded the $limit byte decompressed limit")
            }
        }
        return count
    }

    override fun read(): Int {
        val byte = super.read()
        if (byte >= 0) charge(1)
        return byte
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int = charge(super.read(b, off, len))

    override fun skip(n: Long): Long {
        val skipped = super.skip(n)
        if (skipped > 0) {
            consumed += skipped
            if (consumed > limit) {
                throw NbtSizeLimitExceeded("NBT input exceeded the $limit byte decompressed limit")
            }
        }
        return skipped
    }

    // Marks would let a caller re-read charged bytes without being charged again, which would
    // defeat the cap. DataInputStream does not need them.
    override fun markSupported(): Boolean = false
}
