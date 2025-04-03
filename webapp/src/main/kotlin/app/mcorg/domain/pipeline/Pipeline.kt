package app.mcorg.domain.pipeline

interface Step<in I, out E, out S> {
    fun process(input: I): Result<E, S>

    companion object {
        fun <E, V> value(value: V) = object : Step<Any, E, V> {
            override fun process(input: Any): Result<E, V> {
                return Result.success(value)
            }
        }

        fun <E> validate(
            predicate: (Unit) -> Boolean,
            error: E
        ): Step<Unit, E, Unit> {
            return object : Step<Unit, E, Unit> {
                override fun process(input: Unit): Result<E, Unit> {
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

    companion object {
        fun <E, I> create(): Pipeline<I, E, I> = Pipeline { Result.success(it) }
    }
}