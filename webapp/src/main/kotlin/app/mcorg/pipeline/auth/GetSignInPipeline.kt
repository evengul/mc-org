package app.mcorg.pipeline.auth

import app.mcorg.config.AppConfig
import app.mcorg.domain.Local
import app.mcorg.domain.Production
import app.mcorg.domain.Test
import app.mcorg.pipeline.auth.commonsteps.ConvertTokenStep
import app.mcorg.pipeline.auth.commonsteps.GetTokenStep
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.consts.ISSUER
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.landing.landingPage
import app.mcorg.presentation.utils.getHost
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import io.ktor.server.response.*
import java.net.URLEncoder

suspend fun ApplicationCall.handleGetSignIn() {
    val customRedirectPath = parameters["redirect_to"]
    val requestedUsername = when (AppConfig.env) {
        Production -> null
        else -> parameters["username"]
    }

    executePipeline(
        onSuccess = { respondRedirect(it) },
        onFailure = {
            when(it) {
                is AppFailure.AuthError.MissingToken -> respondHtml(landingPage(getSignInUrl(customRedirectPath ?: "/", requestedUsername)))
                is AppFailure.AuthError.ConvertTokenError -> respondRedirect(it.toRedirect().toUrl())
                else -> respondRedirect("/auth/sign-out?error=${it.javaClass.simpleName}")
            }
        }
    ) {
        value(request.cookies)
            .step(GetTokenStep(AUTH_COOKIE))
            .step(ConvertTokenStep(ISSUER))
            .value(customRedirectPath ?: "/app")
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
    return "${AppConfig.microsoftLoginBaseUrl}/consumers/oauth2/v2.0/authorize?response_type=code&scope=openid,XboxLive.signin&client_id=$clientId&redirect_uri=$redirectUrl&state=${URLEncoder.encode(redirectPath, "UTF-8")}"
}