package app.mcorg.infrastructure.reader.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class ListOrStringSerializer : KSerializer<Any> {

    override fun deserialize(decoder: Decoder): Any {
        return try {
            decoder.decodeString()
        } catch (e: Exception) {
            return decoder.decodeSerializableValue(ListSerializer(ListElementSerializer()))
        }
    }

    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("ListOrString")

    override fun serialize(encoder: Encoder, value: Any) {
        throw IllegalCallerException("Cannot be serialized")
    }

    inner class ListElementSerializer : KSerializer<Any> {
        override fun deserialize(decoder: Decoder): Any {
            return try {
                decoder.decodeString()
            } catch (ex: Exception) {
                try {
                    decoder.decodeSerializableValue(ListSerializer(String.serializer()))
                } catch (ex2: Exception) {
                    decoder.decodeSerializableValue(ListSerializer(ListElementSerializer()))
                }
            }
        }

        override val descriptor: SerialDescriptor
            get() = buildClassSerialDescriptor("ListOrStringElement")

        override fun serialize(encoder: Encoder, value: Any) {
            throw IllegalCallerException("Cannot be deserialized")
        }
    }
}
