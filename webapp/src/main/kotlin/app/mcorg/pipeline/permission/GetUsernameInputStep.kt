package app.mcorg.pipeline.permission

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import io.ktor.http.*

sealed interface GetUsernameInputFailure : AddWorldParticipantFailure {
    data object NotPresent : GetUsernameInputFailure
}

object GetUsernameInputStep : Step<Parameters, GetUsernameInputFailure, String> {
    override fun process(input: Parameters): Result<GetUsernameInputFailure, String> {
        return input["newUser"]?.let { Result.success(it) } ?: Result.failure(GetUsernameInputFailure.NotPresent)
    }
}