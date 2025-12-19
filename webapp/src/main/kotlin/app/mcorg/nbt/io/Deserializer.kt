package app.mcorg.nbt.io

import app.mcorg.domain.pipeline.Result
import java.io.InputStream

interface Deserializer <E, S> {
    fun fromStream(stream: InputStream): Result<E, S>

    fun fromBytes(bytes: ByteArray): Result<E, S> {
        return bytes.inputStream().use { stream ->
            fromStream(stream)
        }
    }
}