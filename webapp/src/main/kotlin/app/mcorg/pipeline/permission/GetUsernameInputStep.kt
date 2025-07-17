package app.mcorg.pipeline.permission

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.GetUsernameInputFailure
import io.ktor.http.*

object GetUsernameInputStep : Step<Parameters, GetUsernameInputFailure, String> {
    override suspend fun process(input: Parameters): Result<GetUsernameInputFailure, String> {
        return input["newUser"]?.let { Result.success(it) } ?: Result.failure(GetUsernameInputFailure.NotPresent)
    }
}