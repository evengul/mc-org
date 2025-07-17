package app.mcorg.pipeline.auth

import app.mcorg.domain.model.users.Profile
import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.GetProfileFailure
import app.mcorg.pipeline.profile.GetProfileStep

object GetProfileStepForAuth: Step<User, GetProfileFailure, Profile> {
    override suspend fun process(input: User): Result<GetProfileFailure, Profile> {
        val profile = GetProfileStep.process(input.id)
        if (profile is Result.Failure) {
            return Result.failure(GetProfileFailure.ProfileNotFound)
        }
        return Result.success(profile.getOrNull()!!)
    }
}