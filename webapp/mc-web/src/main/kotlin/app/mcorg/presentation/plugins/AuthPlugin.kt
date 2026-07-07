package app.mcorg.presentation.plugins

import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.pipelineResult
import app.mcorg.pipeline.auth.commonsteps.ConvertTokenStep
import app.mcorg.pipeline.auth.commonsteps.GetTokenStep
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.consts.ISSUER
import app.mcorg.presentation.utils.getHost
import app.mcorg.presentation.utils.removeToken
import app.mcorg.presentation.utils.storeUser
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.path
import io.ktor.server.response.respondRedirect

/**
 * Path prefixes exempt from the JWT sign-in redirect. Static assets are served before auth, and
 * `/integrations/` is the machine-facing surface that carries its own shared-secret gate (e.g.
 * WebhookAdminAuthPlugin) and must not be bounced to the user sign-in flow.
 */
private val AUTH_EXEMPT_PREFIXES = listOf(
    "/static/",
    "/assets/",
    "/favicon.ico",
    "/integrations/",
    "/api/v1",
)

val AuthPlugin = createRouteScopedPlugin("AuthPlugin") {
    onCall {
        val path = it.request.path()
        if (AUTH_EXEMPT_PREFIXES.any { prefix -> path.startsWith(prefix) }) {
            return@onCall
        }
        val result = pipelineResult<AppFailure, Unit> {
            val token = GetTokenStep(AUTH_COOKIE).run(it.request.cookies)
            val user = ConvertTokenStep(ISSUER).run(token)
            it.storeUser(user)
        }
        if (result is Result.Failure && (result.error is AppFailure.Redirect || result.error is AppFailure.AuthError.ConvertTokenError)) {
            it.response.cookies.removeToken(it.getHost() ?: "false")
            val url = when (val error = result.error) {
                is AppFailure.Redirect -> error.toUrl()
                is AppFailure.AuthError.ConvertTokenError -> error.toRedirect().toUrl()
                else -> "/auth/sign-in"
            }
            it.respondRedirect(url, permanent = false)
        } else if (result is Result.Failure && !it.request.path().contains("/auth/sign-in") && !it.request.path().contains("/auth/sign-out") && !it.request.path().contains("/oidc")) {
            it.respondRedirect("/auth/sign-in?redirect_to=${it.request.path()}", permanent = false)
        }
    }
}
