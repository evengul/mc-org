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

data class ByteListTag(override val value: List<Byte>) : Tag<List<Byte>> {
    override val id: Byte = ID

    companion object {
        const val ID = 7.toByte()
    }
}

data class IntListTag(override val value: List<Int>) : Tag<List<Int>> {
    override val id: Byte = ID

    companion object {
        const val ID = 11.toByte()
    }
}

data class LongListTag(override val value: List<Long>) : Tag<List<Long>> {
    override val id: Byte = ID

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