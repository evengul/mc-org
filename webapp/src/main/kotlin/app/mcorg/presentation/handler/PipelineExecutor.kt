package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.*
import app.mcorg.pipeline.failure.AppFailure
import io.ktor.server.application.*

// region Legacy pipeline API (still used by existing handlers)

suspend fun <E : AppFailure, O> ApplicationCall.executePipeline(
    onSuccess: suspend (O) -> Unit,
    onFailure: suspend (E) -> Unit,
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

suspend fun <E : AppFailure, O> ApplicationCall.executePipeline(
    onSuccess: suspend (O) -> Unit,
    block: PipelineBuilder<Unit, E, Unit>.() -> PipelineBuilder<Unit, E, O>
) {
    val defaultOnFailure: suspend (E) -> Unit = { error ->
        this.defaultHandleError(error)
    }
    executePipeline(
        onSuccess = onSuccess,
        onFailure = defaultOnFailure,
        block = block
    )
}

suspend fun <E : AppFailure, O> ApplicationCall.executeParallelPipeline(
    onSuccess: suspend (O) -> Unit,
    onFailure: suspend (E) -> Unit,
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
    onSuccess: suspend (O) -> Unit,
    block: ParallelPipelineBuilder<E>.() -> PipelineRef<O>
) {
    val defaultOnFailure: suspend (E) -> Unit = { error ->
        this.defaultHandleError(error)
    }
    executeParallelPipeline(
        onSuccess = onSuccess,
        onFailure = defaultOnFailure,
        block = block
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

// endregion

// region New simplified pipeline API

/**
 * Executes a pipeline block with short-circuit semantics and default error handling.
 *
 * Inside the block, call `.bind()` on any Result to extract the success value.
 * If any Result is a Failure, the pipeline short-circuits and the default error
 * handler responds with an appropriate HTML error response.
 *
 * Example:
 * ```
 * call.handlePipeline(
 *     onSuccess = { project -> respondHtml(projectCard(project)) }
 * ) {
 *     val input = ValidateInputStep.process(parameters).bind()
 *     val projectId = CreateProjectStep(worldId).process(input).bind()
 *     GetProjectByIdStep.process(projectId).bind()
 * }
 * ```
 */
suspend fun <O> ApplicationCall.handlePipeline(
    onSuccess: suspend (O) -> Unit,
    block: suspend PipelineScope<AppFailure>.() -> O
) {
    pipeline(
        onSuccess = onSuccess,
        onFailure = { error -> this.defaultHandleError(error) },
        block = block
    )
}

/**
 * Executes a pipeline block and returns the Result directly.
 * Useful when you need to compose results outside the callback pattern.
 *
 * Example:
 * ```
 * val result = call.runPipeline {
 *     val world = GetWorldStep.process(worldId).bind()
 *     val members = GetMembersStep.process(worldId).bind()
 *     Pair(world, members)
 * }
 * ```
 */
suspend fun <O> ApplicationCall.runPipeline(
    block: suspend PipelineScope<AppFailure>.() -> O
): Result<AppFailure, O> {
    return pipelineResult(block)
}

// endregion
