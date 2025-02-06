package app.mcorg.infrastructure.reader.serializer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure

class PredicateSerializer : KSerializer<Any?> {
    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): Any? = decoder.decodeStructure(descriptor) {
        loop@ while (true) {
            when (val i = decodeElementIndex(descriptor)) {
                DECODE_DONE -> break@loop
                else -> decodeNullableSerializableElement(descriptor, i, PredicateSerializer())
            }
        }
        return@decodeStructure null
    }

    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("Predicate")

    override fun serialize(encoder: Encoder, value: Any?) {
        throw NotImplementedError()
    }
}