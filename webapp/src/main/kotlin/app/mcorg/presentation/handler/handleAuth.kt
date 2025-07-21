package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.Local
import app.mcorg.domain.Test
import app.mcorg.domain.model.user.MinecraftProfile
import app.mcorg.pipeline.auth.AddCookieStep
import app.mcorg.pipeline.auth.ConvertTokenStep
import app.mcorg.pipeline.auth.CreateTokenStep
import app.mcorg.pipeline.auth.CreateUserIfNotExistsStep
import app.mcorg.pipeline.auth.GetMicrosoftCodeStep
import app.mcorg.pipeline.auth.GetMicrosoftTokenInput
import app.mcorg.pipeline.auth.GetMicrosoftTokenStep
import app.mcorg.pipeline.auth.GetMinecraftProfileStep
import app.mcorg.pipeline.auth.GetMinecraftToken
import app.mcorg.pipeline.failure.GetSignInPageFailure
import app.mcorg.pipeline.auth.GetTokenStep
import app.mcorg.pipeline.auth.GetXboxProfileStep
import app.mcorg.pipeline.auth.GetXstsToken
import app.mcorg.pipeline.failure.MissingToken
import app.mcorg.pipeline.failure.Redirect
import app.mcorg.pipeline.failure.SignInLocallyFailure
import app.mcorg.pipeline.failure.SignInWithMinecraftFailure
import app.mcorg.pipeline.auth.UpdateLastSignInStep
import app.mcorg.pipeline.auth.ValidateEnvStep
import app.mcorg.pipeline.failure.toRedirect
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.consts.ISSUER
import app.mcorg.presentation.templated.landing.landingPage
import app.mcorg.presentation.utils.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.RequestCookies
import io.ktor.server.response.*
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import kotlin.random.Random

suspend fun ApplicationCall.handleGetSignIn() {
    val customRedirectPath = parameters["redirect_to"]
    Pipeline.create<GetSignInPageFailure, RequestCookies>()
        .pipe(GetTokenStep(AUTH_COOKIE))
        .pipe(ConvertTokenStep(ISSUER))
        .map {
            customRedirectPath ?: "/app"
        }
        .mapFailure { it.toRedirect(customRedirectPath) }
        .fold(
            input = request.cookies,
            onSuccess = { respondRedirect(it) },
            onFailure = { when(it) {
                is MissingToken -> respondHtml(landingPage(getSignInUrl(customRedirectPath ?: "/")))
                is Redirect -> respondRedirect(it.url)
            } }
        )
}

suspend fun ApplicationCall.handleLocalSignIn() {
    val localUsername = "evegul"
    val localUuid = "evegul-uuid"
    val redirectPath = parameters["redirect_to"] ?: "/"
    Pipeline.create<SignInLocallyFailure, Unit>()
        .pipe(Step.value(getEnvironment()))
        .pipe(ValidateEnvStep(Local))
        .pipe(Step.value(MinecraftProfile(uuid = localUuid, username = localUsername)))
        .pipe(CreateUserIfNotExistsStep)
        .pipe(CreateTokenStep)
        .pipe(AddCookieStep(response.cookies, getHost() ?: "false"))
        .map { localUsername }
        .pipe(UpdateLastSignInStep)
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
    val testUser = getTestUser()
    Pipeline.create<SignInLocallyFailure, Unit>()
        .pipe(Step.value(getEnvironment()))
        .pipe(ValidateEnvStep(Test))
        .pipe(Step.value(testUser))
        .pipe(CreateUserIfNotExistsStep)
        .pipe(CreateTokenStep)
        .pipe(AddCookieStep(response.cookies, getHost() ?: "false"))
        .map { testUser.username }
        .pipe(UpdateLastSignInStep)
        .fold(
            input = Unit,
            onFailure = { respond(HttpStatusCode.Forbidden) },
            onSuccess = { respondRedirect(redirectPath) }
        )
}

private fun getTestUser(): MinecraftProfile {
    val username = "TestUser_${Random.nextInt(89_999) + 10_000}"
    return MinecraftProfile(
        username = username,
        uuid = UUID.randomUUID().toString()
    )
}

suspend fun ApplicationCall.handleSignIn() {
    val redirectPath = parameters["state"]?.let { URLDecoder.decode(it, Charsets.UTF_8) } ?: "/"
    var username = "system" // Default in case of failure
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
        .pipe(CreateUserIfNotExistsStep) { username = it.minecraftUsername }
        .pipe(CreateTokenStep)
        .pipe(AddCookieStep(response.cookies, getHost() ?: "false"))
        .map { username }
        .pipe(UpdateLastSignInStep)
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