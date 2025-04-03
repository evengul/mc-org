package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import io.ktor.http.Parameters

interface GetMicrosoftCodeFailure : SignInLocallyFailure {
    data class Error(val error: String, val description: String) : GetMicrosoftCodeFailure
    data object MissingCode : GetMicrosoftCodeFailure
}

object GetMicrosoftCodeStep : Step<Parameters, GetMicrosoftCodeFailure, String> {
    override fun process(input: Parameters): Result<GetMicrosoftCodeFailure, String> {
        val code = input["code"]
        if (code != null) {
            return Result.success(code)
        }

        val error = input["error"]
        val description = input["description"]

        return if (error != null) {
            Result.failure(GetMicrosoftCodeFailure.Error(error, description ?: "Some error occurred"))
        } else {
            Result.failure(GetMicrosoftCodeFailure.MissingCode)
        }
    }
}
