package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.*
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import io.ktor.server.application.*

/**
 * Executes a pipeline block with short-circuit semantics and default error handling.
 *
 * Inside the block, call `.run()` on any Step to execute it and automatically
 * short-circuit on failure. The default error handler responds with an appropriate
 * HTML error response.
 *
 * Example:
 * ```
 * call.handlePipeline(
 *     onSuccess = { project -> respondHtml(projectCard(project)) }
 * ) {
 *     val input = ValidateInputStep.run(parameters)
 *     val projectId = CreateProjectStep(worldId).run(input)
 *     GetProjectByIdStep.run(projectId)
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
 *     val world = GetWorldStep.run(worldId)
 *     val members = GetMembersStep.run(worldId)
 *     Pair(world, members)
 * }
 * ```
 */
suspend fun <O> ApplicationCall.runPipeline(
    block: suspend PipelineScope<AppFailure>.() -> O
): Result<AppFailure, O> {
    return pipelineResult(block)
}
