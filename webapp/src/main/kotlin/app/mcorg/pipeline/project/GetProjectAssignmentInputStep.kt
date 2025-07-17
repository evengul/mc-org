package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.GetProjectAssignmentInputStepFailure
import io.ktor.http.Parameters

sealed interface GetProjectAssignmentOutput {
    data object RemoveAssignment : GetProjectAssignmentOutput
    data class AddAssignment(val userId: Int) : GetProjectAssignmentOutput
}

object GetProjectAssignmentInputStep : Step<Parameters, GetProjectAssignmentInputStepFailure, GetProjectAssignmentOutput> {
    override suspend fun process(input: Parameters): Result<GetProjectAssignmentInputStepFailure, GetProjectAssignmentOutput> {
        return when(input["userId"]) {
            null ->  Result.failure(GetProjectAssignmentInputStepFailure.UserIdNotPresent)
            "-1" -> Result.success(GetProjectAssignmentOutput.RemoveAssignment)
            else -> {
                val userId = input["userId"]?.toIntOrNull()
                if (userId == null) {
                    Result.failure(GetProjectAssignmentInputStepFailure.UserIdNotPresent)
                } else {
                    Result.success(GetProjectAssignmentOutput.AddAssignment(userId))
                }
            }
        }
    }
}