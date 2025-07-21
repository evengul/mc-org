package app.mcorg.presentation.plugins

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.AuthPluginFailure
import app.mcorg.pipeline.auth.ConvertTokenStep
import app.mcorg.pipeline.auth.GetTokenStep
import app.mcorg.pipeline.failure.GetCookieFailure
import app.mcorg.pipeline.failure.toRedirect
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.consts.ISSUER
import app.mcorg.presentation.utils.*
import io.ktor.server.application.*
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
        val result = Pipeline.create<AuthPluginFailure, RequestCookies>()
            .pipe(GetTokenStep(AUTH_COOKIE))
            .pipe(ConvertTokenStep(ISSUER))
            .map { user -> it.storeUser(user) }
            .execute(it.request.cookies)
        if (result is Result.Failure && result.error !is GetCookieFailure.MissingCookie) {
            it.response.cookies.removeToken(it.getHost() ?: "localhost")
            val url = result.error.toRedirect("/auth/sign-in?redirect_to=${it.request.path()}").url
            it.respondRedirect(url, permanent = false)
        } else if (result is Result.Failure && !it.request.path().contains("/auth/sign-in") && !it.request.path().contains("/oidc")) {
            it.respondRedirect("/auth/sign-in?redirect_to=${it.request.path()}", permanent = false)
        }
    }
}