package app.mcorg.domain.pipeline

interface Step<in I, out E, out S> {
    suspend fun process(input: I): Result<E, S>

    companion object {
        fun <E, V> value(value: V) = object : Step<Any, E, V> {
            override suspend fun process(input: Any): Result<E, V> {
                return Result.success(value)
            }
        }

        fun <I, E> validate(
            error: E,
            predicate: suspend (I) -> Boolean,
        ): Step<I, E, I> {
            return object : Step<I, E, I> {
                override suspend fun process(input: I): Result<E, I> {
                    return when(predicate(input)) {
                        true -> Result.success(input)
                        false -> Result.failure(error)
                    }
                }
            }
        }
    }
}