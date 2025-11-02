package app.mcorg.presentation.plugins

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.auth.commonsteps.ConvertTokenStep
import app.mcorg.pipeline.auth.commonsteps.GetTokenStep
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.consts.ISSUER
import app.mcorg.presentation.utils.*
import io.ktor.server.application.*
import io.ktor.server.request.RequestCookies
import io.ktor.server.request.path
import io.ktor.server.response.respondRedirect

sealed interface AuthPluginFailures {
    data object MissingCookie : AuthPluginFailures
    data class Redirect(val url: String) : AuthPluginFailures
}

val AuthPlugin = createRouteScopedPlugin("AuthPlugin") {
    onCall {
        if (it.request.path().startsWith("/static/")
            || it.request.path().startsWith("/assets/")
            || it.request.path().startsWith("/favicon.ico")) {
            return@onCall
        }
        val result = Pipeline.create<AuthPluginFailures, RequestCookies>()
            .wrapPipe(GetTokenStep(AUTH_COOKIE)) { stepResult ->
                stepResult.mapError { AuthPluginFailures.MissingCookie }
            }
            .wrapPipe(ConvertTokenStep(ISSUER)) { stepResult ->
                stepResult.mapError { e -> AuthPluginFailures.Redirect(e.toSignOutUrl()) }
            }
            .map { user -> it.storeUser(user) }
            .execute(it.request.cookies)
        if (result is Result.Failure && result.error is AuthPluginFailures.Redirect) {
            it.response.cookies.removeToken(it.getHost() ?: "false")
            it.respondRedirect(result.error.url, permanent = false)
        } else if (result is Result.Failure && !it.request.path().contains("/auth/sign-in") && !it.request.path().contains("/oidc")) {
            it.respondRedirect("/auth/sign-in?redirect_to=${it.request.path()}", permanent = false)
        }
    }
}