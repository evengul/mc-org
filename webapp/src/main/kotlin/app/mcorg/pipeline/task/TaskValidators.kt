package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.PremadeTask
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step

sealed interface TaskValidationFailure : CreateDoableTaskFailure, CreateCountableTaskFailure, CreateLitematicaTasksFailure, EditDoneMoreTaskFailure {
    data object TooShortName : TaskValidationFailure
    data object ShouldBePositive : TaskValidationFailure
    data class Multiple(val failures: List<Pair<PremadeTask, TaskValidationFailure>>) : TaskValidationFailure
}

private const val MIN_TASK_NAME_LENGTH = 3

val ValidateTaskNameStep = Step.validate<String, TaskValidationFailure.TooShortName>(TaskValidationFailure.TooShortName) { it.length >= MIN_TASK_NAME_LENGTH }
val ValidateCountableTaskRequirementsStep = Step.validate<Int, TaskValidationFailure.ShouldBePositive>(TaskValidationFailure.ShouldBePositive) { it > 0 }

object CountableTaskValidator : Step<Pair<String, Int>, TaskValidationFailure, Pair<String, Int>> {
    override suspend fun process(input: Pair<String, Int>): Result<TaskValidationFailure, Pair<String, Int>> {
        return ValidateTaskNameStep.process(input.first)
            .map { ValidateCountableTaskRequirementsStep.process(input.second) }
            .map { input }
    }
}

object LitematicaTasksValidator : Step<List<PremadeTask>, TaskValidationFailure.Multiple, List<PremadeTask>> {
    override suspend fun process(input: List<PremadeTask>): Result<TaskValidationFailure.Multiple, List<PremadeTask>> {
        val errors = mutableListOf<Pair<PremadeTask, TaskValidationFailure>>()
        input.forEach {
            CountableTaskValidator.process(it.name to it.needed)
                .errorOrNull()?.let { error -> errors.add(it to error) }
        }
        return if (errors.isEmpty()) {
            Result.success(input)
        } else {
            Result.failure(TaskValidationFailure.Multiple(errors))
        }
    }
}