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

    /**
     * Several failures from one compound.
     *
     * [toString] is written by hand rather than generated, because the generated one was an
     * out-of-memory primitive. `readCompoundTag` accumulates a failure per malformed child and
     * keeps reading, an unknown tag id costs an attacker three wire bytes, and `LitematicaReader`
     * renders this into a message string — so a 16 kB upload produced ~5.5 M accumulated errors
     * and a **143 MB** `String`, measured, for a message nothing reads. The accumulation itself is
     * now capped at [MAX_ACCUMULATED] upstream; this keeps the rendering bounded regardless of who
     * constructs it.
     */
    data class Multiple(val errors: List<BinaryParseFailure>) : BinaryParseFailure {
        override fun toString(): String {
            val shown = errors.take(RENDER_LIMIT).joinToString(", ")
            val omitted = errors.size - RENDER_LIMIT
            return if (omitted > 0) {
                "Multiple(${errors.size} failures: $shown, and $omitted more)"
            } else {
                "Multiple($shown)"
            }
        }

        companion object {
            /** How many failures [toString] spells out before summarising the rest. */
            const val RENDER_LIMIT = 10

            /**
             * How many failures one compound accumulates before parsing gives up.
             *
             * A document with more malformed children than this is not one a user is going to fix
             * from an error message, so there is nothing to gain by reading the rest of it.
             */
            const val MAX_ACCUMULATED = 64
        }
    }
}