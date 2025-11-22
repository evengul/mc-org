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