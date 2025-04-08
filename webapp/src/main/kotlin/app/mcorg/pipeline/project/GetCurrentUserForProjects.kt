package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.profile.GetProfileStep
import app.mcorg.presentation.handler.GetProjectsData

sealed interface GetProfileForProjectsFailure : GetProjectsFailure {
    data object UserNotFound : GetProfileForProjectsFailure
    data class Other(val failure: DatabaseFailure) : GetProfileForProjectsFailure
}

data class GetProfileForProjects(val userId: Int) : Step<GetProjectsData, GetProfileForProjectsFailure, GetProjectsData> {
    override suspend fun process(input: GetProjectsData): Result<GetProfileForProjectsFailure, GetProjectsData> {
        return GetProfileStep.process(userId)
            .mapError { if (it is DatabaseFailure.NotFound) GetProfileForProjectsFailure.UserNotFound else GetProfileForProjectsFailure.Other(it) }
            .map { input.copy(currentUserProfile = it) }
    }
}
