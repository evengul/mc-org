package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AssignTaskFailure
import app.mcorg.pipeline.failure.EnsureUserExistsInProjectFailure
import app.mcorg.pipeline.project.EnsureUserExistsInProject
import app.mcorg.pipeline.project.GetProjectAssignmentOutput

data class AssignTaskOrRemoveTaskAssignmentStep(val worldId: Int, val taskId: Int) : Step<GetProjectAssignmentOutput, AssignTaskFailure, Unit> {
    override suspend fun process(input: GetProjectAssignmentOutput): Result<AssignTaskFailure, Unit> {
        return when(input) {
            is GetProjectAssignmentOutput.RemoveAssignment -> RemoveTaskAssignmentStep.process(taskId)
            is GetProjectAssignmentOutput.AddAssignment -> Pipeline.create<AssignTaskFailure, Int>()
                .wrapPipe(EnsureUserExistsInProject(worldId)) {
                    when (it) {
                        is Result.Failure -> when (it.error) {
                            is EnsureUserExistsInProjectFailure.UserNotFound -> Result.failure(AssignTaskFailure.UserNotFound)
                            is EnsureUserExistsInProjectFailure.Other -> Result.failure(AssignTaskFailure.Other(it.error.failure))
                        }
                        is Result.Success -> Result.success(it.value)
                    }
                }
                .pipe(AssignTaskStep(taskId))
                .execute(input.userId)
        }
    }
}
