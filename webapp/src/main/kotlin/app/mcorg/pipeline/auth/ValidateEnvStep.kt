package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.model.Env

sealed interface ValidateEnvFailure : SignInLocallyFailure {
    data object InvalidEnv : ValidateEnvFailure
}

data class ValidateEnvStep(val wantedEnv: Env): Step<Env, ValidateEnvFailure, Env> {
    override suspend fun process(input: Env): Result<ValidateEnvFailure, Env> {
        return if (input == wantedEnv) {
            Result.success(input)
        } else {
            Result.failure(ValidateEnvFailure.InvalidEnv)
        }
    }
}