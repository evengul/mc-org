package app.mcorg.domain.pipeline

open class ContextAwareStep<I, C, E, S>(
    private val contextProcessor: (I, C) -> Result<E, S>
) : Step<WithContext<I, C>, E, S> {
    override fun process(input: WithContext<I, C>): Result<E, S> {
        return contextProcessor(input.value, input.context)
    }
}

data class WithContext<V, C>(
    val value: V,
    val context: C
)

fun <I, E, S, C> Pipeline<I, E, S>.withContext(context: C): Pipeline<I, E, WithContext<S, C>> {
    return this.map { WithContext(it, context) }
}

fun <I, E, S> Pipeline<I, E, S>.withValueAsContext(): Pipeline<I, E, WithContext<S, S>> {
    return this.map { value -> WithContext(value, value) }
}

fun <I, E, S, C, R> Pipeline<I, E, WithContext<S, C>>.pipeWithContext(
    nextStep: ContextAwareStep<S, C, E, R>
): Pipeline<I, E, WithContext<R, C>> {
    return Pipeline {
        val intermediate = execute(it)
        intermediate.flatMap { ctx ->
            nextStep.process(ctx).map {
                WithContext(it, ctx.context)
            }
        }
    }
}

fun <I, E, S, C, R> Pipeline<I, E, WithContext<S, C>>.mapValue(
    transform: (S) -> R
): Pipeline<I, E, WithContext<R, C>> {
    return this.map { ctx -> WithContext(transform(ctx.value), ctx.context) }
}

fun <I, E, S, C, NC> Pipeline<I, E, WithContext<S, C>>.mapContext(
    transform: (C) -> NC
): Pipeline<I, E, WithContext<S, NC>> {
    return this.map { ctx -> WithContext(ctx.value, transform(ctx.context)) }
}

fun <I, E, S, C, R, NC> Pipeline<I, E, WithContext<S, C>>.mapBoth(
    transform: (S, C) -> Pair<R, NC>
): Pipeline<I, E, WithContext<R, NC>> {
    return this.map { ctx ->
        val (newValue, newContext) = transform(ctx.value, ctx.context)
        WithContext(newValue, newContext)
    }
}

fun <I, E, S, C> Pipeline<I, E, WithContext<S, C>>.extractValue(): Pipeline<I, E, S> {
    return this.map { it.value }
}