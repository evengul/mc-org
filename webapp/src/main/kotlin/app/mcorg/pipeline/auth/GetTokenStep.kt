package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import io.ktor.server.request.RequestCookies

data class GetTokenStep(val cookieName: String): Step<RequestCookies, GetTokenStepFailure, String> {
    override fun process(input: RequestCookies): Result<GetTokenStepFailure, String> {
        return when(val cookie = input[cookieName]) {
            null -> Result.failure(GetTokenStepFailure.TokenNotFound)
            else -> Result.success(cookie)
        }
    }
}