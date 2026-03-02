package app.mcorg.nbt.failure

sealed interface NBTFailure {
    object UnknownCompressionType : NBTFailure
    data class DeserializeError(val cause: String) : NBTFailure
    object InvalidStructure : NBTFailure
    data class MissingData(val fields: List<String>) : NBTFailure
}
