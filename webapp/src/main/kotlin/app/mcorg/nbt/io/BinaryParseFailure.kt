package app.mcorg.nbt.io

sealed interface BinaryParseFailure {
    sealed interface MaxDepthFailure : BinaryParseFailure {
        data object NegativeDepth : MaxDepthFailure
        data object MaxDepthReached : MaxDepthFailure

        companion object {
            fun from(failure: app.mcorg.nbt.io.MaxDepthFailure): BinaryParseFailure {
                return when(failure) {
                    is app.mcorg.nbt.io.MaxDepthFailure.MaxDepthReached -> MaxDepthReached
                    is app.mcorg.nbt.io.MaxDepthFailure.NegativeDepth -> NegativeDepth
                }
            }
        }
    }
    data class UnknownTagType(val type: Byte) : BinaryParseFailure
    data class ReadError(val message: String) : BinaryParseFailure
    data class Unknown(val cause: Exception) : BinaryParseFailure

    data class Multiple(val errors: List<BinaryParseFailure>) : BinaryParseFailure
}