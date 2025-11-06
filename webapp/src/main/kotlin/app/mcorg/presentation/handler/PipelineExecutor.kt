package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.*
import app.mcorg.pipeline.failure.AppFailure
import io.ktor.server.application.*

suspend fun <E : AppFailure, O> ApplicationCall.executePipeline(
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

suspend fun <E : AppFailure, O> ApplicationCall.executeParallelPipeline(
    onSuccess: suspend (O) -> Unit = { },
    onFailure: suspend (E) -> Unit = {

    },
    block: ParallelPipelineBuilder<E>.() -> PipelineRef<O>
) {
    val pipeline = parallelPipeline(block)
    pipeline.fold(
        input = Unit,
        onSuccess = {
            @Suppress("UNCHECKED_CAST")
            onSuccess(it as O)
        },
        onFailure = {
            onFailure(it)
        }
    )
}

suspend fun <E : AppFailure, O> ApplicationCall.executeParallelPipeline(
    block: ParallelPipelineBuilder<E>.() -> PipelineRef<O>
): Result<E, O> {
    val pipeline = parallelPipeline(block)
    return pipeline.execute(input = Unit)
        .map {
            @Suppress("UNCHECKED_CAST")
            it as O
        }
}
