package app.mcorg.nbt.failure

sealed interface NBTFailure {
    object UnknownCompressionType : NBTFailure
    object DeserializeError : NBTFailure
    object InvalidStructure : NBTFailure
    data class MissingData(val fields: List<String>) : NBTFailure
}
