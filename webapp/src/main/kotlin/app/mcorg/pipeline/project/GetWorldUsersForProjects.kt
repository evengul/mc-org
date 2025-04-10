package app.mcorg.pipeline.project

import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.permission.GetOtherUsersFailure
import app.mcorg.pipeline.permission.GetOtherUsersStep
import app.mcorg.pipeline.permission.VerifyParticipantAdderIsAdminFailure
import app.mcorg.presentation.handler.GetUsersData

sealed interface GetWorldUsersForProjectsFailure : GetProjectsFailure, CreateProjectFailure, GetProjectFailure {
    data class Other(val failure: DatabaseFailure) : GetWorldUsersForProjectsFailure
    data object NotAdmin : GetWorldUsersForProjectsFailure
}

data class GetWorldUsersForProjects(val worldId: Int) : Step<User, GetWorldUsersForProjectsFailure, List<User>> {
    override suspend fun process(input: User): Result<GetWorldUsersForProjectsFailure, List<User>> {
        return GetOtherUsersStep.process(GetUsersData(worldId, input.id))
            .mapError { when(it) {
                is GetOtherUsersFailure.Other -> GetWorldUsersForProjectsFailure.Other(it.failure)
                VerifyParticipantAdderIsAdminFailure.NotAdmin -> GetWorldUsersForProjectsFailure.NotAdmin
                is VerifyParticipantAdderIsAdminFailure.Other -> GetWorldUsersForProjectsFailure.Other(it.failure)
            } }
            .map { it.users + listOf(input) }
    }
}