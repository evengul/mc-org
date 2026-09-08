package app.mcorg.pipeline

/**
 * The unit of work in a pipeline: one input in, one [Result] out.
 *
 * A `fun interface`, so the class ceremony is optional. A step that needs no constructor state
 * is a lambda:
 * ```
 * val lengthStep = Step<String, Nothing, Int> { input -> Result.success(input.length) }
 * ```
 * and a step that carries state (a user, a world id, a transaction connection) or that a test
 * wants to name is a class or object implementing [process]. Both shapes are the same type, so
 * `DatabaseSteps.query(...)`, `ValidationSteps.required(...)` and a hand-written class compose
 * identically inside a pipeline (MCO-553).
 */
fun interface Step<in I, out E, out S> {
    suspend fun process(input: I): Result<E, S>
}
