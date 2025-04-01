package app.mcorg.domain.pipeline

interface Step<in I, out E, out S> {
    fun process(input: I): Result<E, S>
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