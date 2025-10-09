package app.mcorg.domain.pipeline

import org.slf4j.LoggerFactory

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

    fun <R> pipe(nextStep: Step<S, @UnsafeVariance E, R>, peekFunk: suspend (R) -> Unit): Pipeline<I, E, R> {
        return Pipeline {
            val result = execute(it).flatMap(nextStep::process)
            result.peek(peekFunk)
            result
        }
    }

    fun <E0, S0, R> wrapPipe(
        nextStep: Step<S, E0, S0>,
        wrapFunc: suspend (Result<E0, S0>) -> Result<@UnsafeVariance E, @UnsafeVariance R>
    ): Pipeline<I, E, R> {
        return Pipeline {
            val wrapStep = object : Step<@UnsafeVariance S, E, R> {
                override suspend fun process(input: @UnsafeVariance S): Result<E, R> {
                    return wrapFunc(nextStep.process(input))
                }
            }
            val intermediate = execute(it)
            intermediate.flatMap(wrapStep::process)
        }
    }

    fun <R> andThen(nextPipeline: Pipeline<S, @UnsafeVariance E, R>): Pipeline<I, E, R> {
        return Pipeline { input ->
            val result = execute(input)
            when (result) {
                is Result.Success -> nextPipeline.execute(result.value)
                is Result.Failure -> Result.failure(result.error)
            }
        }
    }

    fun <R> map(transform: suspend (S) -> R): Pipeline<I, E, R> {
        return Pipeline {
            val intermediate = execute(it)
            intermediate.map(transform)
        }
    }

    fun peek(peekFunc: suspend (S) -> Unit): Pipeline<I, E, S> {
        return Pipeline {
            val intermediate = execute(it)
            intermediate.peek(peekFunc)
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

    suspend fun fold(
        input: I,
        onSuccess: suspend (S) -> Unit,
        onFailure: suspend (E) -> Unit
    ) {
        val logger = LoggerFactory.getLogger(Pipeline::class.java)
        execute(input).fold(onSuccess) {
            logger.error("Pipeline failed with error: $it")
            onFailure(it)
        }
    }

    companion object {
        fun <E, I> create(): Pipeline<I, E, I> = Pipeline { Result.success(it) }
    }
}