package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.PipelineBuilder
import app.mcorg.domain.pipeline.pipeline
import app.mcorg.domain.pipeline.parallelPipeline
import app.mcorg.domain.pipeline.ParallelPipelineBuilder
import app.mcorg.domain.pipeline.PipelineRef
import io.ktor.server.application.ApplicationCall

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

suspend fun <E, O> ApplicationCall.executeParallelPipeline(
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
