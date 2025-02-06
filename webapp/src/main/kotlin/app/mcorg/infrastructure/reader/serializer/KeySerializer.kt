package app.mcorg.infrastructure.reader.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class KeySerializer : KSerializer<Any> {
    override fun deserialize(decoder: Decoder): Any {
        return decoder.decodeSerializableValue(MapSerializer(Char.serializer(), ListOrStringSerializer()))
    }

    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("KeySerializer")

    override fun serialize(encoder: Encoder, value: Any) {
        throw IllegalCallerException("Cannot be serialized")
    }
}