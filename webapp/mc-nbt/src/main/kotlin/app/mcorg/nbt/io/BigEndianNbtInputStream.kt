package app.mcorg.nbt.io

import app.mcorg.pipeline.Result
import app.mcorg.nbt.tag.*
import java.io.DataInputStream
import java.io.InputStream

/**
 * Reads NBT from an untrusted stream.
 *
 * Every declared length in NBT is attacker-controlled, so the stream is wrapped in a
 * [BoundedInputStream] and each allocation is cross-checked against the bytes that stream can
 * still supply (MCO-345). Without that check `ByteArray(readInt())` is an out-of-memory primitive
 * costing the attacker five bytes.
 */
class BigEndianNbtInputStream private constructor(
    private val bounded: BoundedInputStream,
) : DataInputStream(bounded), NbtInput<Any>, MaxDepthIO {

    constructor(
        inputStream: InputStream,
        limit: Long = NbtLimits.MAX_DECOMPRESSED_BYTES,
    ) : this(BoundedInputStream(inputStream, limit))

    /**
     * Rejects a declared element count that the remaining input could not possibly satisfy.
     *
     * The check is deliberately about *bytes still readable*, not about a fixed element ceiling:
     * a file may only claim what it is prepared to supply, which makes the bound self-scaling and
     * leaves legitimate documents untouched.
     */
    private fun checkedLength(declared: Int, bytesPerElement: Long, what: String): Int {
        if (declared < 0) {
            throw NbtSizeLimitExceeded("$what declared a negative length ($declared)")
        }
        val needed = declared.toLong() * bytesPerElement
        val available = bounded.remaining()
        if (needed > available) {
            throw NbtSizeLimitExceeded(
                "$what declared $declared elements needing $needed bytes, but only $available remain"
            )
        }
        return declared
    }

    override fun readTag(maxDepth: Int): Result<BinaryParseFailure, NamedTag<*>> {
        val id = Result.tryCatch(
            { BinaryParseFailure.ReadError(it.message ?: "Could not read tag id") },
            { readByte() }
        )
        if (id is Result.Failure) {
            return id
        }

        val name = Result.tryCatch(
            { BinaryParseFailure.ReadError(it.message ?: "Could not read tag name") },
            { readUTF() }
        )

        if (name is Result.Failure) {
            return name
        }

        val tag = readTag(id.getOrThrow(), maxDepth)

        return tag.mapSuccess { NamedTag(name.getOrThrow(), it) }
    }

    fun readTag(type: Byte, maxDepth: Int) : Result<BinaryParseFailure, Tag<*>> {
        val reader = readers[type] ?: return Result.failure(
            BinaryParseFailure.UnknownTagType(type)
        )
        return reader(this, maxDepth)
    }

    override fun readRawTag(maxDepth: Int): Result<BinaryParseFailure, Tag<*>> {
        val id = Result.tryCatch(
            { BinaryParseFailure.ReadError(it.message ?: "Could not read tag id") },
            { readByte() }
        )
        if (id is Result.Failure) {
            return id
        }

        return readTag(id.getOrThrow(), maxDepth)
    }

    fun readByteTag() = tryRead { ByteTag(readByte()) }
    fun readShortTag() = tryRead { ShortTag(readShort()) }
    fun readIntTag() = tryRead { IntTag(readInt()) }
    fun readLongTag() = tryRead { LongTag(readLong()) }
    fun readFloatTag() = tryRead { FloatTag(readFloat()) }
    fun readDoubleTag() = tryRead { DoubleTag(readDouble()) }
    fun readStringTag() = tryRead { StringTag(readUTF()) }

    fun readByteListTag() = tryRead {
        val byteArray = ByteArray(checkedLength(readInt(), 1, "TAG_Byte_Array"))
        readFully(byteArray)
        ByteListTag(byteArray)
    }

    fun readIntListTag() = tryRead {
        val intArray = IntArray(checkedLength(readInt(), 4, "TAG_Int_Array"))
        for (i in intArray.indices) {
            intArray[i] = readInt()
        }
        IntListTag(intArray)
    }

    fun readLongListTag() = tryRead {
        val longArray = LongArray(checkedLength(readInt(), 8, "TAG_Long_Array"))
        for (i in longArray.indices) {
            longArray[i] = readLong()
        }
        LongListTag(longArray)
    }

    fun readUnknownListTag(maxDepth: Int) = tryRead {
        val type = readByte()
        val clazz = idClassMapping[type] ?: throw IllegalStateException("Unknown tag type $type in unknown list tag")

        val tag = ListTag<Any>(id = type)

        val length = readInt().coerceAtLeast(0)

        // A TAG_End reader consumes zero bytes, so a list of them is the one case the
        // bytes-remaining check below cannot bound — 0x7FFFFFFF no-op appends from a handful of
        // bytes. An empty list is still written with element type 0, so only a non-zero length
        // is rejected.
        if (type == EndTag.id && length > 0) {
            throw NbtSizeLimitExceeded("TAG_List of TAG_End declared $length elements; TAG_End consumes no bytes")
        }
        checkedLength(length, NbtLimits.minimumEncodedSize(type), "TAG_List of tag type $type")

        repeat(length) {
            val newDepth = decrementMaxDepth(maxDepth)
            if (newDepth is Result.Failure) {
                throw IllegalStateException("Max depth reached when reading unknown list tag")
            }
            when (val result = readTag(type, newDepth.getOrThrow())) {
                is Result.Success -> {
                    if (!clazz.isInstance(result.value)) {
                        throw IllegalStateException("Expected tag of type ${clazz.name} but got ${result.value::class.java.name} in unknown list tag")
                    }
                    tag.value.add(result.value as Any)
                }
                is Result.Failure -> throw IllegalStateException("Could not read tag of type $type in unknown list tag: ${result.error}")
            }
        }

        tag
    }

    fun readCompoundTag(maxDepth: Int): Result<BinaryParseFailure, CompoundTag> {
        val errors = mutableListOf<BinaryParseFailure>()
        val compoundTag = CompoundTag()

        while (true) {
            val idResult = readByteTag()
            if (idResult is Result.Failure) {
                return Result.failure(idResult.error)
            }
            val id = (idResult as Result.Success).value.value.toInt() and 0xFF
            if (id == 0) {
                break
            }

            val keyResult: Result<BinaryParseFailure, String> = Result.tryCatch(
                { BinaryParseFailure.ReadError(it.message ?: "Could not read tag key") },
                { readUTF() }
            )

            val key = when (keyResult) {
                is Result.Success -> keyResult.value
                is Result.Failure -> {
                    errors += keyResult.error
                    continue
                }
            }

            val decrementedMaxDepth = when (val result = decrementMaxDepth(maxDepth)) {
                is Result.Success -> result
                is Result.Failure -> Result.failure(BinaryParseFailure.MaxDepthFailure.from(result.error))
            }
            val tagResult = decrementedMaxDepth.flatMapSuccess { readTag(id.toByte(), it) }

            when (tagResult) {
                is Result.Success -> compoundTag.value[key] = tagResult.value
                is Result.Failure -> {
                    errors += tagResult.error
                    continue
                }
            }
        }

        return if (errors.isEmpty()) {
            Result.success(compoundTag)
        } else {
            Result.failure(BinaryParseFailure.Multiple(errors))
        }
    }

    fun <T> tryRead(block: () -> Tag<T>): Result<BinaryParseFailure, Tag<T>> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(BinaryParseFailure.ReadError(e.message ?: "Could not read tag"))
        }
    }

    companion object {
        private val readers: Map<Byte, (BigEndianNbtInputStream, Int) -> Result<BinaryParseFailure, Tag<*>>> = mapOf(
            EndTag.id to { _, _ -> Result.success(EndTag) },
            ByteTag.ID to { input, _ -> input.readByteTag() },
            ShortTag.ID to { input, _ -> input.readShortTag() },
            IntTag.ID to { input, _ -> input.readIntTag() },
            LongTag.ID to { input, _ -> input.readLongTag() },
            FloatTag.ID to { input, _ -> input.readFloatTag() },
            DoubleTag.ID to { input, _ -> input.readDoubleTag() },
            StringTag.ID to { input, _ -> input.readStringTag() },
            ByteListTag.ID to { input, _ -> input.readByteListTag() },
            IntListTag.ID to { input, _ -> input.readIntListTag() },
            LongListTag.ID to { input, _ -> input.readLongListTag() },
            ListTag.ID to { input, maxDepth -> input.readUnknownListTag(maxDepth) },
            CompoundTag.ID to { input, maxDepth -> input.readCompoundTag(maxDepth) }
        )

        private val idClassMapping: Map<Byte, Class<out Tag<*>>> = mapOf(
            EndTag.id to EndTag::class.java,
            ByteTag.ID to ByteTag::class.java,
            ShortTag.ID to ShortTag::class.java,
            IntTag.ID to IntTag::class.java,
            LongTag.ID to LongTag::class.java,
            FloatTag.ID to FloatTag::class.java,
            DoubleTag.ID to DoubleTag::class.java,
            StringTag.ID to StringTag::class.java,
            ByteListTag.ID to ByteListTag::class.java,
            IntListTag.ID to IntListTag::class.java,
            LongListTag.ID to LongListTag::class.java,
            ListTag.ID to ListTag::class.java,
            CompoundTag.ID to CompoundTag::class.java
        )
    }
}
