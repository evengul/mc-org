package app.mcorg.pipeline.auth

import app.mcorg.config.AppConfig
import app.mcorg.domain.Local
import app.mcorg.domain.Production
import app.mcorg.domain.Test
import app.mcorg.domain.pipeline.pipeline
import app.mcorg.pipeline.auth.commonsteps.ConvertTokenStep
import app.mcorg.pipeline.auth.commonsteps.GetTokenStep
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.consts.ISSUER
import app.mcorg.presentation.security.encodeOAuthState
import app.mcorg.presentation.security.newOAuthNonce
import app.mcorg.presentation.security.safeRedirectPath
import app.mcorg.presentation.security.setOAuthNonce
import app.mcorg.presentation.templated.landing.landingPage
import app.mcorg.presentation.utils.getHost
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import io.ktor.server.response.*
import java.net.URLEncoder

suspend fun ApplicationCall.handleGetSignIn() {
    // Validated once here so every downstream use — the landing page's sign-in URL, the OAuth
    // state, and the post-sign-in redirect below — carries the same same-origin guarantee
    // (MCO-352). `null` rather than the default when absent, so the "/worlds" and "/" defaults
    // each branch keeps below still apply.
    val customRedirectPath = parameters["redirect_to"]?.let { safeRedirectPath(it) }
    val requestedUsername = when (AppConfig.env) {
        Production -> null
        else -> parameters["username"]
    }

    pipeline(
        onSuccess = { url: String -> respondRedirect(url) },
        onFailure = { error: AppFailure ->
            when(error) {
                is AppFailure.AuthError.MissingToken -> respondHtml(landingPage(getSignInUrl(customRedirectPath ?: "/", requestedUsername)))
                is AppFailure.AuthError.ConvertTokenError -> respondRedirect(error.toRedirect().toUrl())
                else -> respondRedirect("/auth/sign-out?error=${error.javaClass.simpleName}")
            }
        }
    ) {
        val token = GetTokenStep(AUTH_COOKIE).run(request.cookies)
        ConvertTokenStep(ISSUER).run(token)
        customRedirectPath ?: "/worlds"
    }
}

private fun ApplicationCall.getSignInUrl(redirectPath: String = "/", requestedUsername: String?): String {
    return if (AppConfig.skipMicrosoftSignIn) {
        when (AppConfig.env) {
            Local -> "/auth/oidc/demo-redirect?redirect_to=${URLEncoder.encode(redirectPath, "UTF-8")}${if (requestedUsername != null) "&username=${URLEncoder.encode(requestedUsername, "UTF-8")}" else ""}"
            Test -> "/auth/oidc/demo-redirect?redirect_to=${URLEncoder.encode(redirectPath, "UTF-8")}&username=${requestedUsername ?: "random"}"
            Production -> "/auth/sign-out?error=misconfigured&message=${URLEncoder.encode("Cannot skip microsoft sign-in in production environment.", "UTF-8")}"
        }
    } else getMicrosoftSignInUrl(redirectPath)
}

private fun ApplicationCall.getMicrosoftSignInUrl(redirectPath: String): String {
    val clientId = AppConfig.microsoftClientId
    val env = AppConfig.env
    val host = getHost()
    val redirectUrl =
        if (env == Local) "http://localhost:8080/auth/oidc/microsoft-redirect"
        else "https://$host/auth/oidc/microsoft-redirect"

    // state now carries a nonce alongside the redirect path, with its twin in a short-lived
    // cookie (MCO-355). The callback requires both and rejects a mismatch before spending the
    // code, which is what stops an attacker feeding a victim their own authorization code.
    val nonce = newOAuthNonce()
    response.cookies.setOAuthNonce(nonce)
    val state = encodeOAuthState(nonce, redirectPath)

    return "${AppConfig.microsoftLoginBaseUrl}/consumers/oauth2/v2.0/authorize?response_type=code&scope=openid,XboxLive.signin&client_id=$clientId&redirect_uri=$redirectUrl&state=${URLEncoder.encode(state, "UTF-8")}"
}
