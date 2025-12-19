package app.mcorg.nbt.io

import app.mcorg.domain.pipeline.Result
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

enum class CompressionType(
    val id: Byte,
    val decompressor: (InputStream) -> Result<BinaryParseFailure, InputStream>
) {
    NONE(0, { input -> Result.success(input) }),
    GZIP(
        1,
        { input -> Result.tryCatch({ BinaryParseFailure.Unknown(it) }, { GZIPInputStream(input) }) }
    ),
    ZLIB(
        2,
        { input -> Result.tryCatch({ BinaryParseFailure.Unknown(it) }, { InflaterInputStream(input) }) }
    );

    fun decompress(input: InputStream): Result<BinaryParseFailure, InputStream> {
        return decompressor(input)
    }

    companion object {
        fun detect(bytes: ByteArray): Result<BinaryParseFailure, CompressionType> {
            if (bytes.size >= 2) {
                if (bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
                    return Result.success(GZIP)
                }
                if (bytes[0] == 0x78.toByte() && bytes[1] == 0x9C.toByte()) {
                    return Result.success(ZLIB)
                }
            }
            return Result.success(NONE)
        }
    }
}