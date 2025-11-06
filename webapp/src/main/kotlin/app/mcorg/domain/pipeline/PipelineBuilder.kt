package app.mcorg.domain.pipeline

class PipelineBuilder<I, E, O>(
    private val currentPipeline: Pipeline<I, E, O>
) {
    fun <S> value(value: S): PipelineBuilder<I, E, S> {
        return map { value }
    }

    fun <S> step(step: Step<O, E, S>): PipelineBuilder<I, E, S> {
        return PipelineBuilder(currentPipeline.pipe(step))
    }

    fun <S> map(transform: suspend (O) -> S): PipelineBuilder<I, E, S> {
        return PipelineBuilder(currentPipeline.map(transform))
    }

    fun validate(error: E, predicate: suspend (O) -> Boolean): PipelineBuilder<I, E, O> {
        return PipelineBuilder(currentPipeline.pipe(Step.validate(error, predicate)))
    }

    fun <S> transform(transform: suspend (O) -> S): PipelineBuilder<I, E, S> {
        return PipelineBuilder(currentPipeline.map(transform))
    }

    suspend fun execute(
        input: I,
        onSuccess: suspend (O) -> Unit,
        onFailure: suspend (E) -> Unit
    ) {
        currentPipeline.fold(input, onSuccess, onFailure)
    }
}

fun <I, E> pipeline(): PipelineBuilder<I, E, I> {
    return PipelineBuilder(Pipeline.create())
}
