package app.mcorg.pipeline.project

import app.mcorg.domain.model.users.Profile
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.profile.GetProfileStep

sealed interface GetProfileForProjectsFailure : GetProjectsFailure {
    data object UserNotFound : GetProfileForProjectsFailure
    data class Other(val failure: DatabaseFailure) : GetProfileForProjectsFailure
}

data class GetProfileForProjects(val userId: Int) : Step<Unit, GetProfileForProjectsFailure, Profile> {
    override suspend fun process(input: Unit): Result<GetProfileForProjectsFailure, Profile> {
        return GetProfileStep.process(userId)
            .mapError { if (it is DatabaseFailure.NotFound) GetProfileForProjectsFailure.UserNotFound else GetProfileForProjectsFailure.Other(it) }
    }
}
