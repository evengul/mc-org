package app.mcorg.pipeline.project

import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.GetOtherUsersStepFailure
import app.mcorg.pipeline.failure.GetWorldUsersForProjectsFailure
import app.mcorg.pipeline.permission.GetOtherUsersStep
import app.mcorg.presentation.handler.GetUsersData

data class GetWorldUsersForProjects(val worldId: Int) : Step<User, GetWorldUsersForProjectsFailure, List<User>> {
    override suspend fun process(input: User): Result<GetWorldUsersForProjectsFailure, List<User>> {
        return GetOtherUsersStep.process(GetUsersData(worldId, input.id))
            .mapError { when(it) {
                is GetOtherUsersStepFailure.Other -> GetWorldUsersForProjectsFailure.Other(it.failure)
            } }
            .map { it.users + listOf(input) }
    }
}