package app.mcorg.pipeline.auth.commonsteps

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.consts.AUTH_COOKIE
import io.ktor.server.request.*

data class GetTokenStep(val cookieName: String = AUTH_COOKIE): Step<RequestCookies, AppFailure.AuthError.MissingToken, String> {
    override suspend fun process(input: RequestCookies): Result<AppFailure.AuthError.MissingToken, String> {
        return when(val cookie = input[cookieName]) {
            null -> Result.failure(AppFailure.AuthError.MissingToken)
            else -> Result.success(cookie)
        }
    }
}