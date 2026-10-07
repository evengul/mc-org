package app.mcorg.nbt.io

import app.mcorg.pipeline.Result
import app.mcorg.nbt.tag.NamedTag
import app.mcorg.nbt.tag.Tag
import java.io.InputStream

/**
 * A deserializer for NBT data in binary format.
 * @param T The type of the root tag.
 * @property compressionType The type of compression used in the NBT data.
 * @property maxDepth How deeply compounds and lists may nest; see [Tag.DEFAULT_MAX_DEPTH].
 */
class BinaryNbtDeserializer<T>(
    val compressionType: CompressionType,
    val maxDepth: Int = Tag.DEFAULT_MAX_DEPTH,
) : Deserializer<BinaryParseFailure, NamedTag<T>> {
    override fun fromStream(stream: InputStream): Result<BinaryParseFailure, NamedTag<T>> {
        return compressionType.decompress(stream).mapSuccess { BigEndianNbtInputStream(it) }.flatMapSuccess {
            try {
                @Suppress("UNCHECKED_CAST")
                it.readTag(maxDepth) as Result<BinaryParseFailure, NamedTag<T>>
            } catch (e: StackOverflowError) {
                // [maxDepth] is sized to keep a parse well inside the stack, so this should not
                // fire. If it does (a smaller -Xss, a caller already deep) it is still a document
                // we cannot read, and an Error would escape every Result-based caller, all of which
                // catch Exception. The stack has unwound by the time we are here.
                Result.failure(BinaryParseFailure.ReadError("NBT nesting exceeded the parser's stack"))
            }
        }
    }
}
