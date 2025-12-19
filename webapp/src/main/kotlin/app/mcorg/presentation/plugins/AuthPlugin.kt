package app.mcorg.presentation.plugins

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.auth.commonsteps.ConvertTokenStep
import app.mcorg.pipeline.auth.commonsteps.GetTokenStep
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.consts.ISSUER
import app.mcorg.presentation.utils.getHost
import app.mcorg.presentation.utils.removeToken
import app.mcorg.presentation.utils.storeUser
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.RequestCookies
import io.ktor.server.request.path
import io.ktor.server.response.respondRedirect

val AuthPlugin = createRouteScopedPlugin("AuthPlugin") {
    onCall {
        if (it.request.path().startsWith("/static/")
            || it.request.path().startsWith("/assets/")
            || it.request.path().startsWith("/favicon.ico")) {
            return@onCall
        }
        val result = Pipeline.create<AppFailure, RequestCookies>()
            .pipe(GetTokenStep(AUTH_COOKIE))
            .pipe(ConvertTokenStep(ISSUER))
            .map { user -> it.storeUser(user) }
            .execute(it.request.cookies)
        if (result is Result.Failure && (result.error is AppFailure.Redirect || result.error is AppFailure.AuthError.ConvertTokenError)) {
            it.response.cookies.removeToken(it.getHost() ?: "false")
            val url = when (result.error) {
                is AppFailure.Redirect -> result.error.toUrl()
                is AppFailure.AuthError.ConvertTokenError -> result.error.toRedirect().toUrl()
                else -> "/auth/sign-in"
            }
            it.respondRedirect(url, permanent = false)
        } else if (result is Result.Failure && !it.request.path().contains("/auth/sign-in") && !it.request.path().contains("/auth/sign-out") && !it.request.path().contains("/oidc")) {
            it.respondRedirect("/auth/sign-in?redirect_to=${it.request.path()}", permanent = false)
        }
    }
}