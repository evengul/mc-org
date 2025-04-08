package app.mcorg.domain.pipeline

interface Step<in I, out E, out S> {
    fun process(input: I): Result<E, S>

    companion object {
        fun <E, V> value(value: V) = object : Step<Any, E, V> {
            override fun process(input: Any): Result<E, V> {
                return Result.success(value)
            }
        }

        fun <I, E> validate(
            error: E,
            predicate: (I) -> Boolean,
        ): Step<I, E, I> {
            return object : Step<I, E, I> {
                override fun process(input: I): Result<E, I> {
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
    private val processor: (I) -> Result<E, S>
) {
    fun execute(input: I): Result<E, S> {
        return processor(input)
    }

    fun <R> pipe(nextStep: Step<S, @UnsafeVariance E, R>): Pipeline<I, E, R> {
        return Pipeline {
            val intermediate = execute(it)
            intermediate.flatMap { nextStep.process(it) }
        }
    }

    fun <R> map(transform: (S) -> R): Pipeline<I, E, R> {
        return Pipeline {
            val intermediate = execute(it)
            intermediate.map(transform)
        }
    }

    fun <F> mapFailure(transform: (E) -> F): Pipeline<I, F, S> {
        return Pipeline {
            val intermediate = execute(it)
            intermediate.mapError(transform)
        }
    }

    fun recover(transform: (E) -> Result<@UnsafeVariance E, @UnsafeVariance S>): Pipeline<I, E, S> {
        return Pipeline {
            val intermediate = execute(it)
            intermediate.recover(transform)
        }
    }

    companion object {
        fun <E, I> create(): Pipeline<I, E, I> = Pipeline { Result.success(it) }
    }
}