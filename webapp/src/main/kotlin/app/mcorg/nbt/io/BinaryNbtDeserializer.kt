package app.mcorg.nbt.io

import app.mcorg.domain.pipeline.Result
import app.mcorg.nbt.tag.NamedTag
import app.mcorg.nbt.tag.Tag
import java.io.InputStream

/**
 * A deserializer for NBT data in binary format.
 * @param T The type of the root tag.
 * @property compressionType The type of compression used in the NBT data.
 */
class BinaryNbtDeserializer<T>(
    val compressionType: CompressionType,
) : Deserializer<BinaryParseFailure, NamedTag<T>> {
    override fun fromStream(stream: InputStream): Result<BinaryParseFailure, NamedTag<T>> {
        return compressionType.decompress(stream).mapSuccess { BigEndianNbtInputStream(it) }.flatMapSuccess {
            @Suppress("UNCHECKED_CAST")
            it.readTag(Tag.DEFAULT_MAX_DEPTH) as Result<BinaryParseFailure, NamedTag<T>>
        }
    }
}