package app.mcorg.nbt.tag

sealed interface Tag<T> {
    val value: T
    val id: Byte

    companion object {
        const val DEFAULT_MAX_DEPTH = 512
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