package app.mcorg.presentation.plugins

import app.mcorg.pipeline.Result
import app.mcorg.pipeline.pipelineResult
import app.mcorg.pipeline.auth.commonsteps.ConvertTokenStep
import app.mcorg.pipeline.auth.commonsteps.GetTokenStep
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.consts.ISSUER
import app.mcorg.presentation.utils.getHost
import app.mcorg.presentation.utils.pageUri
import app.mcorg.presentation.utils.redirectClientOrBrowser
import app.mcorg.presentation.utils.removeToken
import app.mcorg.presentation.utils.signInRedirectUrl
import app.mcorg.presentation.utils.storeUser
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.path

/**
 * Path prefixes exempt from the JWT sign-in redirect. Static assets are served before auth, and
 * `/integrations/` is the machine-facing surface that carries its own shared-secret gate (e.g.
 * MachineEndpointAuthPlugin) and must not be bounced to the user sign-in flow.
 */
private val AUTH_EXEMPT_PREFIXES = listOf(
    "/static/",
    "/assets/",
    "/favicon.ico",
    "/integrations/",
    "/api/v1",
)

/**
 * Health endpoints, exempt by **exact** match rather than prefix (MCO-349).
 *
 * A platform health check arrives with no cookie, so without this it is redirected to sign-in and
 * Fly reads the 302 as unhealthy — every machine would be marked down by the very check meant to
 * confirm it is up. Neither endpoint discloses anything: `/test/ping` is a constant, `/test/ready`
 * is READY or NOT READY.
 *
 * Exact match, not a `/test/` prefix, so a future route under `/test` is authenticated by default
 * rather than exempt by accident.
 */
private val AUTH_EXEMPT_PATHS = setOf(
    "/test/ping",
    "/test/ready",
)

/**
 * Both redirects go through [redirectClientOrBrowser] (MCO-590). A plain 302 to an HTMX request is
 * followed inside the fetch, and htmx swaps the whole sign-in or sign-out page into whatever
 * `hx-target` the button named — so a session that expires on an open page turned the next click
 * into a brand bar inside a list row.
 */
val AuthPlugin = createRouteScopedPlugin("AuthPlugin") {
    onUnansweredCall {
        val path = it.request.path()
        if (path in AUTH_EXEMPT_PATHS || AUTH_EXEMPT_PREFIXES.any { prefix -> path.startsWith(prefix) }) {
            return@onUnansweredCall
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
            it.redirectClientOrBrowser(url)
        } else if (result is Result.Failure && !path.contains("/auth/sign-in") && !path.contains("/auth/sign-out") && !path.contains("/oidc")) {
            it.redirectClientOrBrowser(signInRedirectUrl(it.pageUri()))
        }
    }
}
