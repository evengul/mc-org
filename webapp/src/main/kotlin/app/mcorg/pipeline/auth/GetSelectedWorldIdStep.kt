package app.mcorg.pipeline.auth

import app.mcorg.domain.model.users.Profile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.GetSelectedWorldStepFailure

object GetSelectedWorldIdStep : Step<Profile, GetSelectedWorldStepFailure, Int> {
    override suspend fun process(input: Profile): Result<GetSelectedWorldStepFailure, Int> {
        return when(val selectedWorld = input.selectedWorld) {
            null -> Result.failure(GetSelectedWorldStepFailure.NoSelectedWorld)
            else -> Result.success(selectedWorld)
        }
    }
}