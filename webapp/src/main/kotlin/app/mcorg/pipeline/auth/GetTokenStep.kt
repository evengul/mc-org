package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.presentation.consts.AUTH_COOKIE
import io.ktor.server.request.RequestCookies

sealed interface GetCookieFailure : GetSignInPageFailure, AuthPluginFailure {
    data object MissingCookie : GetCookieFailure
}

data class GetTokenStep(val cookieName: String = AUTH_COOKIE): Step<RequestCookies, GetCookieFailure, String> {
    override suspend fun process(input: RequestCookies): Result<GetCookieFailure, String> {
        return when(val cookie = input[cookieName]) {
            null -> Result.failure(GetCookieFailure.MissingCookie)
            else -> Result.success(cookie)
        }
    }
}