package app.mcorg.nbt.tag

sealed interface Tag<T> {
    val value: T
    val id: Byte

    companion object {
        /**
         * How deeply compounds and lists may nest before the parse is refused.
         *
         * The parser recurses once per level, so this is a promise about stack (MCO-426). The
         * deepest nesting that parses on a fresh thread, worst of compounds vs lists and of
         * JIT-compiled vs interpreted:
         *
         * | `-Xss`                                  | deepest that parses |
         * |-----------------------------------------|---------------------|
         * | 1 MB (JVM default; prod sets no `-Xss`) | ~1000               |
         * | 512 kB                                  | ~440                |
         * | 256 kB                                  | ~165                |
         *
         * 128 leaves ~8x on the production stack, room for the Ktor and coroutine frames beneath a
         * request, and still parses on 256 kB.
         * Real files are nowhere near it: the deepest test fixture, a stocked shulker loader, nests
         * 11 levels, and each layer of items-in-containers adds only a few.
         *
         * Re-measure if the reader's recursion changes shape: nest compounds and lists separately,
         * one `-Xss` per JVM (a per-thread stack size is only a hint), and under `-Xint` as well.
         * If the stack is exceeded anyway, [app.mcorg.nbt.io.BinaryNbtDeserializer] turns the Error
         * into a failure; but an overflow during class initialisation poisons that class for the
         * JVM's lifetime, which is why the margin matters more than the backstop.
         */
        const val DEFAULT_MAX_DEPTH = 128
    }
}

object EndTag : Tag<Unit> {
    override val value: Unit = Unit
    override val id: Byte = 0.toByte()
}

data class ByteTag(override val value: Byte) : Tag<Byte> {
    override val id: Byte = ID

    companion object {
        const val ID = 1.toByte()
    }
}

data class ShortTag(override val value: Short) : Tag<Short> {
    override val id: Byte = ID

    companion object {
        const val ID = 2.toByte()
    }
}

data class IntTag(override val value: Int) : Tag<Int> {
    override val id: Byte = ID

    companion object {
        const val ID = 3.toByte()
    }
}

data class LongTag(override val value: Long) : Tag<Long> {
    override val id: Byte = ID

    companion object {
        const val ID = 4.toByte()
    }
}

data class FloatTag(override val value: Float) : Tag<Float> {
    override val id: Byte = ID

    companion object {
        const val ID = 5.toByte()
    }
}

data class DoubleTag(override val value: Double) : Tag<Double> {
    override val id: Byte = ID

    companion object {
        const val ID = 6.toByte()
    }
}

data class StringTag(override val value: String) : Tag<String> {
    override val id: Byte = ID

    companion object {
        const val ID = 8.toByte()
    }
}

data class ListTag<T>(override val value: MutableList<T> = mutableListOf(), override val id: Byte) : Tag<MutableList<T>> {
    companion object {
        const val ID = 9.toByte()
    }
}

/*
 * The three array tags hold primitive arrays rather than List<Byte>/List<Int>/List<Long>.
 *
 * Boxing them cost roughly sixteen bytes of object header and reference per element, which turned
 * a bounded input into an unbounded heap cost — the amplification half of MCO-345. These are
 * plain classes, not data classes: an array's generated equals() compares identity, which reads
 * as a value comparison and silently is not one.
 */

class ByteListTag(override val value: ByteArray) : Tag<ByteArray> {
    override val id: Byte = ID

    override fun equals(other: Any?): Boolean =
        this === other || (other is ByteListTag && value.contentEquals(other.value))

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = "ByteListTag(size=${value.size})"

    companion object {
        const val ID = 7.toByte()
    }
}

class IntListTag(override val value: IntArray) : Tag<IntArray> {
    override val id: Byte = ID

    override fun equals(other: Any?): Boolean =
        this === other || (other is IntListTag && value.contentEquals(other.value))

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = "IntListTag(size=${value.size})"

    companion object {
        const val ID = 11.toByte()
    }
}

class LongListTag(override val value: LongArray) : Tag<LongArray> {
    override val id: Byte = ID

    override fun equals(other: Any?): Boolean =
        this === other || (other is LongListTag && value.contentEquals(other.value))

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = "LongListTag(size=${value.size})"

    companion object {
        const val ID = 12.toByte()
    }
}

data class CompoundTag(override val value: MutableMap<String, Tag<*>> = mutableMapOf()) : Tag<Map<String, Tag<*>>> {
    override val id: Byte = ID

    companion object {
        const val ID = 10.toByte()
    }
}