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
                    return if (predicate(input)) {
                        Result.success(input)
                    } else {
                        Result.failure(error)
                    }
                }
            }
        }
    }
}

class Pipeline<in I, out E, out S>(
    private val processor: suspend (I) -> Result<E, S>
) {
    suspend fun execute(input: I): Result<E, S> {
        return processor(input)
    }

    fun <R> pipe(nextStep: Step<S, @UnsafeVariance E, R>): Pipeline<I, E, R> {
        return Pipeline {
            val intermediate = execute(it)
            intermediate.flatMap(nextStep::process)
        }
    }

    fun <R> map(transform: suspend (S) -> R): Pipeline<I, E, R> {
        return Pipeline {
            val intermediate = execute(it)
            intermediate.map(transform)
        }
    }

    fun <F> mapFailure(transform: suspend (E) -> F): Pipeline<I, F, S> {
        return Pipeline {
            val intermediate = execute(it)
            intermediate.mapError(transform)
        }
    }

    fun recover(transform: suspend (E) -> Result<@UnsafeVariance E, @UnsafeVariance S>): Pipeline<I, E, S> {
        return Pipeline {
            val intermediate = execute(it)
            intermediate.recover(transform)
        }
    }

    companion object {
        fun <E, I> create(): Pipeline<I, E, I> = Pipeline { Result.success(it) }
    }
}