package app.mcorg.pipeline.auth.commonsteps

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure

data class SetActiveWorldInput(val profile: TokenProfile, val worldId: Int)

class SetActiveWorldStep : Step<SetActiveWorldInput, AppFailure.AuthError.CouldNotCreateToken, String> {
    override suspend fun process(input: SetActiveWorldInput): Result<AppFailure.AuthError.CouldNotCreateToken, String> {
        return CreateTokenStep.process(input.profile.copy(activeWorldId = input.worldId))
    }
}
