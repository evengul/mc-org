package app.mcorg.pipeline.auth

import app.mcorg.domain.api.Users
import app.mcorg.domain.model.users.Profile
import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result

sealed interface GetProfileFailure : GetSignInPageFailure {
    data object ProfileNotFound : GetProfileFailure
}

data class GetProfileStep(val api: Users): Step<User, GetProfileFailure, Profile> {

    override fun process(input: User): Result<GetProfileFailure, Profile> {
        val profile = api.getProfile(input.id)
        return if (profile != null) {
            Result.success(profile)
        } else {
            Result.failure(GetProfileFailure.ProfileNotFound)
        }
    }
}