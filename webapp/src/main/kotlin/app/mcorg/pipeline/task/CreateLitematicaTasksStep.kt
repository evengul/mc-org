package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.PremadeTask
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.failure.CreateCountableStepFailure
import app.mcorg.pipeline.failure.CreateLitematicaTasksStepFailure

data class CreateLitematicaTasksStep(val projectId: Int, val currentUsername: String) : Step<List<PremadeTask>, CreateLitematicaTasksStepFailure, Unit> {
    override suspend fun process(input: List<PremadeTask>): Result<CreateLitematicaTasksStepFailure, Unit> {
        val errors = mutableListOf<Pair<PremadeTask, DatabaseFailure>>()
        input.forEach {
            CreateCountableTaskStep(projectId, currentUsername).process(it.name to it.needed)
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