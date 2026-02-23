package app.mcorg.domain.pipeline

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * A scoped DSL for railway-oriented programming with short-circuit semantics.
 *
 * Inside a [pipeline] block, call [bind] on any [Result] to extract the success value.
 * If the Result is a Failure, the pipeline short-circuits immediately and the failure
 * is passed to the onFailure handler.
 *
 * Example:
 * ```
 * pipeline(
 *     onSuccess = { project -> respondHtml(projectCard(project)) }
 * ) {
 *     val input = ValidateInputStep.process(parameters).bind()
 *     val projectId = CreateProjectStep(worldId).process(input).bind()
 *     GetProjectByIdStep.process(projectId).bind()
 * }
 * ```
 */
class PipelineScope<E> {

    /**
     * Extracts the success value from a Result, or short-circuits the pipeline with the error.
     */
    fun <S> Result<E, S>.bind(): S = when (this) {
        is Result.Success -> value
        is Result.Failure -> throw PipelineFailure(error)
    }

    /**
     * Runs a Step on the given input and binds the result.
     * Shorthand for `step.process(input).bind()`.
     */
    suspend fun <I, S> Step<I, E, S>.run(input: I): S = process(input).bind()

    /**
     * Runs two independent operations in parallel and returns both results.
     * If either fails, the pipeline short-circuits with the first failure.
     */
    suspend fun <A, B> parallel(
        blockA: suspend PipelineScope<E>.() -> A,
        blockB: suspend PipelineScope<E>.() -> B,
    ): Pair<A, B> = coroutineScope {
        val scopeA = PipelineScope<E>()
        val scopeB = PipelineScope<E>()
        val deferredA = async { scopeA.blockA() }
        val deferredB = async { scopeB.blockB() }
        Pair(deferredA.await(), deferredB.await())
    }

    /**
     * Runs three independent operations in parallel and returns all three results.
     * If any fails, the pipeline short-circuits with the first failure.
     */
    suspend fun <A, B, C> parallel(
        blockA: suspend PipelineScope<E>.() -> A,
        blockB: suspend PipelineScope<E>.() -> B,
        blockC: suspend PipelineScope<E>.() -> C,
    ): Triple<A, B, C> = coroutineScope {
        val scopeA = PipelineScope<E>()
        val scopeB = PipelineScope<E>()
        val scopeC = PipelineScope<E>()
        val deferredA = async { scopeA.blockA() }
        val deferredB = async { scopeB.blockB() }
        val deferredC = async { scopeC.blockC() }
        Triple(deferredA.await(), deferredB.await(), deferredC.await())
    }

    /**
     * Runs four independent operations in parallel.
     * If any fails, the pipeline short-circuits with the first failure.
     */
    suspend fun <A, B, C, D> parallel(
        blockA: suspend PipelineScope<E>.() -> A,
        blockB: suspend PipelineScope<E>.() -> B,
        blockC: suspend PipelineScope<E>.() -> C,
        blockD: suspend PipelineScope<E>.() -> D,
    ): Quadruple<A, B, C, D> = coroutineScope {
        val scopeA = PipelineScope<E>()
        val scopeB = PipelineScope<E>()
        val scopeC = PipelineScope<E>()
        val scopeD = PipelineScope<E>()
        val deferredA = async { scopeA.blockA() }
        val deferredB = async { scopeB.blockB() }
        val deferredC = async { scopeC.blockC() }
        val deferredD = async { scopeD.blockD() }
        Quadruple(deferredA.await(), deferredB.await(), deferredC.await(), deferredD.await())
    }

    /**
     * Internal exception used for short-circuit control flow.
     * Not meant to propagate outside of [pipeline] blocks.
     */
    internal class PipelineFailure(val error: Any?) : Exception("Pipeline short-circuit (not a real error)")
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * Executes a pipeline block with short-circuit semantics.
 *
 * Inside the block, use [PipelineScope.bind] on [Result] values to extract success values.
 * If any Result is a Failure, execution stops and [onFailure] is called with the error.
 * If all steps succeed, [onSuccess] is called with the final value.
 *
 * @param onSuccess Called with the final value when the pipeline completes successfully
 * @param onFailure Called with the error when any step fails
 * @param block The pipeline logic, executed within a [PipelineScope]
 */
suspend fun <E, O> pipeline(
    onSuccess: suspend (O) -> Unit,
    onFailure: suspend (E) -> Unit,
    block: suspend PipelineScope<E>.() -> O
) {
    val scope = PipelineScope<E>()
    try {
        val result = scope.block()
        onSuccess(result)
    } catch (e: PipelineScope.PipelineFailure) {
        @Suppress("UNCHECKED_CAST")
        onFailure(e.error as E)
    }
}

/**
 * Executes a pipeline block and returns the Result directly.
 * Useful when you need the Result value rather than callbacks.
 */
suspend fun <E, O> pipelineResult(
    block: suspend PipelineScope<E>.() -> O
): Result<E, O> {
    val scope = PipelineScope<E>()
    return try {
        Result.success(scope.block())
    } catch (e: PipelineScope.PipelineFailure) {
        @Suppress("UNCHECKED_CAST")
        Result.failure(e.error as E)
    }
}
