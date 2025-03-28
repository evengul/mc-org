package app.mcorg.domain.pipeline

interface Step<I, E : Failure, S> {
    fun process(input: I): Result<E, S>
}

class Pipeline<I, E : Failure, S>(
    private val currentStep: Step<I, E, S>,
) {
    fun pipe(nextStep: Step<S, E, S>): Pipeline<I, E, S> {
        return Pipeline(
            object : Step<I, E, S> {
                override fun process(input: I): Result<E, S> {
                    return currentStep.process(input).andThen { nextStep.process(it) }
                }
            }
        )
    }

    fun execute(input: I): Result<E, S> {
        return currentStep.process(input)
    }
}