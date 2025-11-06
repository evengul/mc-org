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

    fun <R> map(transform: suspend (S) -> R): Pipeline<I, E, R> {
        return Pipeline {
            val intermediate = execute(it)
            intermediate.map(transform)
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