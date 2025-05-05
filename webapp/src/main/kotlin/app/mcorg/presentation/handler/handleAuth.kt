package app.mcorg.presentation.handler

import app.mcorg.domain.model.minecraft.MinecraftProfile
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.Local
import app.mcorg.domain.Test
import app.mcorg.pipeline.auth.AddCookieStep
import app.mcorg.pipeline.auth.ConvertTokenStep
import app.mcorg.pipeline.auth.CreateTokenStep
import app.mcorg.pipeline.auth.CreateUserIfNotExistsStep
import app.mcorg.pipeline.auth.GetMicrosoftCodeStep
import app.mcorg.pipeline.auth.GetMicrosoftTokenInput
import app.mcorg.pipeline.auth.GetMicrosoftTokenStep
import app.mcorg.pipeline.auth.GetMinecraftProfileStep
import app.mcorg.pipeline.auth.GetMinecraftToken
import app.mcorg.pipeline.auth.GetProfileStepForAuth
import app.mcorg.pipeline.auth.GetSelectedWorldIdStep
import app.mcorg.pipeline.auth.GetSignInPageFailure
import app.mcorg.pipeline.auth.GetTokenStep
import app.mcorg.pipeline.auth.GetXboxProfileStep
import app.mcorg.pipeline.auth.GetXstsToken
import app.mcorg.pipeline.auth.MissingToken
import app.mcorg.pipeline.auth.Redirect
import app.mcorg.pipeline.auth.SignInLocallyFailure
import app.mcorg.pipeline.auth.SignInWithMinecraftFailure
import app.mcorg.pipeline.auth.ValidateEnvStep
import app.mcorg.pipeline.auth.toRedirect
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.consts.ISSUER
import app.mcorg.presentation.templates.auth.signInTemplate
import app.mcorg.presentation.utils.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.RequestCookies
import io.ktor.server.response.*
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.random.Random

suspend fun ApplicationCall.handleGetSignIn() {
    val customRedirectPath = parameters["redirect_to"]
    Pipeline.create<GetSignInPageFailure, RequestCookies>()
        .pipe(GetTokenStep(AUTH_COOKIE))
        .pipe(ConvertTokenStep(ISSUER))
        .pipe(GetProfileStepForAuth)
        .pipe(GetSelectedWorldIdStep)
        .map {
            customRedirectPath ?: "/app/worlds/$it/projects"
        }
        .mapFailure { it.toRedirect(customRedirectPath) }
        .fold(
            input = request.cookies,
            onSuccess = { respondRedirect(it) },
            onFailure = { when(it) {
                is MissingToken -> respondHtml(signInTemplate(getSignInUrl(customRedirectPath ?: "/")))
                is Redirect -> respondRedirect(it.url)
            } }
        )
}

suspend fun ApplicationCall.handleLocalSignIn() {
    val redirectPath = parameters["redirect_to"] ?: "/"
    Pipeline.create<SignInLocallyFailure, Unit>()
        .pipe(Step.value(getEnvironment()))
        .pipe(ValidateEnvStep(Local))
        .pipe(Step.value(MinecraftProfile("evegul", "test@example.com")))
        .pipe(CreateUserIfNotExistsStep)
        .pipe(CreateTokenStep)
        .pipe(AddCookieStep(response.cookies, getHost() ?: "false"))
        .fold(
            input = Unit,
            onFailure = { respond(HttpStatusCode.Forbidden) },
            onSuccess = {
                respondRedirect(redirectPath)
            }
        )
}

suspend fun ApplicationCall.handleTestSignIn() {
    val redirectPath = parameters["redirect_to"] ?: "/"
    Pipeline.create<SignInLocallyFailure, Unit>()
        .pipe(Step.value(getEnvironment()))
        .pipe(ValidateEnvStep(Test))
        .pipe(Step.value(getTestUser()))
        .pipe(CreateUserIfNotExistsStep)
        .pipe(CreateTokenStep)
        .pipe(AddCookieStep(response.cookies, getHost() ?: "false"))
        .fold(
            input = Unit,
            onFailure = { respond(HttpStatusCode.Forbidden) },
            onSuccess = { respondRedirect(redirectPath) }
        )
}

private fun getTestUser(): MinecraftProfile {
    val username = "TestUser_${Random.nextInt(100_000)}"
    return MinecraftProfile(
        username,
        "test-$username@mcorg.app"
    )
}

suspend fun ApplicationCall.handleSignIn() {
    val redirectPath = parameters["state"]?.let { URLDecoder.decode(it, Charsets.UTF_8) } ?: "/"
    Pipeline.create<SignInWithMinecraftFailure, Unit>()
        .pipe(Step.value(parameters))
        .pipe(GetMicrosoftCodeStep)
        .map {
            GetMicrosoftTokenInput(
                code = it,
                clientId = getMicrosoftClientId(),
                clientSecret = getMicrosoftClientSecret(),
                env = getEnvironment(),
                host = getHost()
            )
        }
        .pipe(GetMicrosoftTokenStep)
        .pipe(GetXboxProfileStep)
        .pipe(GetXstsToken)
        .pipe(GetMinecraftToken)
        .pipe(GetMinecraftProfileStep)
        .pipe(CreateUserIfNotExistsStep)
        .pipe(CreateTokenStep)
        .pipe(AddCookieStep(response.cookies, getHost() ?: "false"))
        .fold(
            input = Unit,
            onFailure = { respondRedirect(it.toRedirect().url) },
            onSuccess = { respondRedirect(redirectPath) }
        )
}

suspend fun ApplicationCall.handleGetSignOut() {
    response.cookies.removeToken(getHost() ?: "localhost")
    respondRedirect("/", permanent = false)
}

private fun ApplicationCall.getSignInUrl(redirectPath: String = "/"): String {
    return if (getEnvironment() == Test) {
        "/auth/oidc/test-redirect?redirect_to=$redirectPath"
    } else if (getSkipMicrosoftSignIn().lowercase() != "true") {
        getMicrosoftSignInUrl(redirectPath)
    } else {
        "/auth/oidc/local-redirect?redirect_to=$redirectPath"
    }
}

private fun ApplicationCall.getMicrosoftSignInUrl(redirectPath: String): String {
    val clientId = getMicrosoftClientId()
    val env = getEnvironment()
    val host = getHost()
    val redirectUrl =
        if (env == Local) "http://localhost:8080/auth/oidc/microsoft-redirect"
        else "https://$host/auth/oidc/microsoft-redirect"
    return "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?response_type=code&scope=openid,XboxLive.signin&client_id=$clientId&redirect_uri=$redirectUrl&state=${URLEncoder.encode(redirectPath, "UTF-8")}"
}