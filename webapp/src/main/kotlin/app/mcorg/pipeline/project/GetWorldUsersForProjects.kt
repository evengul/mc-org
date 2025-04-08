package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.permission.GetOtherUsersFailure
import app.mcorg.pipeline.permission.GetOtherUsersStep
import app.mcorg.pipeline.permission.VerifyParticipantAdderIsAdminFailure
import app.mcorg.presentation.handler.GetProjectsData
import app.mcorg.presentation.handler.GetUsersData

sealed interface GetWorldUsersForProjectsFailure : GetProjectsFailure {
    data class Other(val failure: DatabaseFailure) : GetWorldUsersForProjectsFailure
    data object NotAdmin : GetWorldUsersForProjectsFailure
}

object GetWorldUsersForProjects : Step<GetProjectsData, GetWorldUsersForProjectsFailure, GetProjectsData> {
    override suspend fun process(input: GetProjectsData): Result<GetWorldUsersForProjectsFailure, GetProjectsData> {
        return GetOtherUsersStep.process(GetUsersData(input.worldId, input.currentUserProfile.id))
            .mapError { when(it) {
                is GetOtherUsersFailure.Other -> GetWorldUsersForProjectsFailure.Other(it.failure)
                VerifyParticipantAdderIsAdminFailure.NotAdmin -> GetWorldUsersForProjectsFailure.NotAdmin
                is VerifyParticipantAdderIsAdminFailure.Other -> GetWorldUsersForProjectsFailure.Other(it.failure)
            } }
            .map { input.copy(users = it.users + listOf(input.currentUserProfile.toUser())) }
    }
}