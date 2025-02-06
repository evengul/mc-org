package app.mcorg.infrastructure.reader.serializer

import app.mcorg.infrastructure.reader.entities.loot.LootPoolEntryFunctionCount
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject

class LootPoolEntryFunctionCountSerializer : KSerializer<Any> {
    override fun deserialize(decoder: Decoder): Any {
        return try {
            decoder.decodeDouble()
        } catch (ex: Exception) {
            val jsonObject = decoder.decodeSerializableValue(JsonObject.serializer())
            return LootPoolEntryFunctionCount(
                jsonObject["type"],
                jsonObject["min"],
                jsonObject["max"]
            )
        }
    }

    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("LootPoolEntryFunctionCount")

    override fun serialize(encoder: Encoder, value: Any) {
        TODO("Not yet implemented")
    }
}