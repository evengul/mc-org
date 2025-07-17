package app.mcorg.pipeline.permission

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.GetUserIdInputFailure
import io.ktor.http.*

object GetUserIdInputStep : Step<Parameters, GetUserIdInputFailure, Int> {
    override suspend fun process(input: Parameters): Result<GetUserIdInputFailure, Int> {
        return input["userId"]?.toIntOrNull()?.let { Result.success(it) } ?: Result.failure(GetUserIdInputFailure.NotPresent)
    }
}