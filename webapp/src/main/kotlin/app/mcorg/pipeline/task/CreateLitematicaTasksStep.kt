package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.PremadeTask
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure

sealed interface CreateLitematicaTasksStepFailure : CreateLitematicaTasksFailure {
    data class ItemFailure(
        val errors: List<Pair<PremadeTask, DatabaseFailure>>
    ) : CreateLitematicaTasksStepFailure
}

data class CreateLitematicaTasksStep(val projectId: Int) : Step<List<PremadeTask>, CreateLitematicaTasksStepFailure, Unit> {
    override suspend fun process(input: List<PremadeTask>): Result<CreateLitematicaTasksStepFailure, Unit> {
        val errors = mutableListOf<Pair<PremadeTask, DatabaseFailure>>()
        input.forEach {
            CreateCountableTaskStep(projectId).process(it.name to it.needed)
                .errorOrNull()?.let { error -> when(error) {
                    is CreateCountableStepFailure.Other -> errors.add(it to error.failure)
                } }
        }

        return errors
            .takeIf { it.isNotEmpty() }
            ?.let { Result.failure(CreateLitematicaTasksStepFailure.ItemFailure(it)) }
            ?: Result.success()
    }
}