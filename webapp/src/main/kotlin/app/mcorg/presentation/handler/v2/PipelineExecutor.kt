package app.mcorg.presentation.handler.v2

import app.mcorg.domain.pipeline.PipelineBuilder
import app.mcorg.domain.pipeline.pipeline
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.parallelPipeline
import app.mcorg.domain.pipeline.ParallelPipelineBuilder
import app.mcorg.domain.pipeline.PipelineRef
import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

suspend fun <E, O> ApplicationCall.executePipeline(
    onSuccess: suspend (O) -> Unit = { },
    onFailure: suspend (E) -> Unit = { },
    block: PipelineBuilder<Unit, E, Unit>.() -> PipelineBuilder<Unit, E, O>
) {
    val builder = pipeline<Unit, E>()
    val builtPipeline = builder.block()
    builtPipeline.execute(
        input = Unit,
        onSuccess = onSuccess,
        onFailure = onFailure
    )
}

suspend fun <E> ApplicationCall.executeParallelPipelines(
    pipelines: Map<String, Pair<Pipeline<*, E, *>, Any>>,
    onSuccess: suspend (Map<String, Any>) -> Unit = { },
    onFailure: suspend (E) -> Unit = { }
) {
    coroutineScope {
        val deferredResults = pipelines.map { (key, pipelinePair) ->
            val (pipeline, input) = pipelinePair
            key to async {
                @Suppress("UNCHECKED_CAST")
                val typedPipeline = pipeline as Pipeline<Any, E, Any>
                typedPipeline.execute(input)
            }
        }

        val results = mutableMapOf<String, Any>()

        for ((key, deferred) in deferredResults) {
            when (val result = deferred.await()) {
                is Result.Success -> results[key] = result.value
                is Result.Failure -> {
                    onFailure(result.error)
                    return@coroutineScope
                }
            }
        }

        onSuccess(results)
    }
}

suspend fun <E, O> ApplicationCall.executeParallelPipelineDSL(
    onSuccess: suspend (O) -> Unit = { },
    onFailure: suspend (E) -> Unit = { },
    block: ParallelPipelineBuilder<E>.() -> PipelineRef<O>
) {
    val pipeline = parallelPipeline(block)
    pipeline.fold(
        input = Unit,
        onSuccess = {
            @Suppress("UNCHECKED_CAST")
            onSuccess(it as O)
        },
        onFailure = onFailure
    )
}
