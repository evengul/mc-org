package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AssignProjectFailure

data class AssignProjectOrRemoveProjectAssignmentStep(val worldId: Int, val projectId: Int) : Step<GetProjectAssignmentOutput, AssignProjectFailure, Unit> {
    override suspend fun process(input: GetProjectAssignmentOutput): Result<AssignProjectFailure, Unit> {
        return when (input) {
            is GetProjectAssignmentOutput.RemoveAssignment -> RemoveProjectAssignmentStep(projectId).process(Unit)
            is GetProjectAssignmentOutput.AddAssignment -> Pipeline.create<AssignProjectFailure, Int>()
                .pipe(EnsureUserExistsInProject(worldId))
                .pipe(AssignUserToProjectStep(projectId))
                .execute(input.userId)
        }
    }
}