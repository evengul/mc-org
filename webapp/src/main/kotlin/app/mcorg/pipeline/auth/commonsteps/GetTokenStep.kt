package app.mcorg.pipeline.auth.commonsteps

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.presentation.consts.AUTH_COOKIE
import io.ktor.server.request.RequestCookies

data object MissingCookieFailure

data class GetTokenStep(val cookieName: String = AUTH_COOKIE): Step<RequestCookies, MissingCookieFailure, String> {
    override suspend fun process(input: RequestCookies): Result<MissingCookieFailure, String> {
        return when(val cookie = input[cookieName]) {
            null -> Result.failure(MissingCookieFailure)
            else -> Result.success(cookie)
        }
    }
}