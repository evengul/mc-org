package app.mcorg.presentation.handler

import app.mcorg.domain.model.minecraft.MinecraftProfile
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.model.Local
import app.mcorg.model.Test
import app.mcorg.pipeline.RedirectStep
import app.mcorg.pipeline.auth.AddCookieStep
import app.mcorg.pipeline.auth.ConvertTokenStep
import app.mcorg.pipeline.auth.CreateTokenStep
import app.mcorg.pipeline.auth.CreateUserIfNotExistsStep
import app.mcorg.pipeline.auth.GetMicrosoftCodeStep
import app.mcorg.pipeline.auth.GetMicrosoftTokenInput
import app.mcorg.pipeline.auth.GetMicrosoftTokenStep
import app.mcorg.pipeline.auth.GetMinecraftProfileStep
import app.mcorg.pipeline.auth.GetMinecraftToken
import app.mcorg.pipeline.auth.GetProfileStep
import app.mcorg.pipeline.auth.GetSelectedWorldIdStep
import app.mcorg.pipeline.auth.GetSignInPageFailure
import app.mcorg.pipeline.auth.GetTokenStep
import app.mcorg.pipeline.auth.GetXboxProfileStep
import app.mcorg.pipeline.auth.GetXstsToken
import app.mcorg.pipeline.auth.SignInLocallyFailure
import app.mcorg.pipeline.auth.SignInWithMinecraftFailure
import app.mcorg.pipeline.auth.ValidateEnvStep
import app.mcorg.pipeline.auth.toRedirect
import app.mcorg.presentation.configuration.*
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.consts.ISSUER
import app.mcorg.presentation.templates.auth.signInTemplate
import app.mcorg.presentation.utils.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.RequestCookies
import io.ktor.server.response.*
import kotlin.random.Random

suspend fun ApplicationCall.handleGetSignIn() {
    val result = Pipeline.create<GetSignInPageFailure, RequestCookies>()
        .pipe(GetTokenStep(AUTH_COOKIE))
        .pipe(ConvertTokenStep(ISSUER))
        .pipe(GetProfileStep(usersApi))
        .pipe(GetSelectedWorldIdStep)
        .pipe(RedirectStep {
            "/app/worlds/$it/projects"
        })
        .mapFailure { it.toRedirect() }.execute(request.cookies)
    when(result) {
        is Result.Success -> respondHtml(signInTemplate(result.value))
        is Result.Failure -> respondRedirect(result.error.url)
    }
}

suspend fun ApplicationCall.handleLocalSignIn() {
    val result = Pipeline.create<SignInLocallyFailure, Unit>()
        .pipe(Step.value(getEnvironment()))
        .pipe(ValidateEnvStep(Local))
        .pipe(Step.value(MinecraftProfile("evegul", "test@example.com")))
        .pipe(CreateUserIfNotExistsStep(usersApi))
        .pipe(CreateTokenStep)
        .pipe(AddCookieStep(response.cookies, getHost() ?: "false"))
        .execute(Unit)
    when(result) {
        is Result.Success -> respondRedirect("/")
        is Result.Failure -> respond(HttpStatusCode.Forbidden)
    }
}

suspend fun ApplicationCall.handleTestSignIn() {
    val result = Pipeline.create<SignInLocallyFailure, Unit>()
        .pipe(Step.value(getEnvironment()))
        .pipe(ValidateEnvStep(Test))
        .pipe(Step.value(getTestUser()))
        .pipe(CreateUserIfNotExistsStep(usersApi))
        .pipe(CreateTokenStep)
        .pipe(AddCookieStep(response.cookies, getHost() ?: "false"))
        .execute(Unit)
    when(result) {
        is Result.Success -> respondRedirect("/")
        is Result.Failure -> respond(HttpStatusCode.Forbidden)
    }
}

private fun getTestUser(): MinecraftProfile {
    val username = "TestUser_${Random.nextInt(100_000)}"
    return MinecraftProfile(
        username,
        "test-$username@mcorg.app"
    )
}

suspend fun ApplicationCall.handleSignIn() {
    val result = Pipeline.create<SignInWithMinecraftFailure, Unit>()
        .pipe(Step.value(parameters))
        .pipe(GetMicrosoftCodeStep)
        .pipe(object : Step<String, SignInWithMinecraftFailure, GetMicrosoftTokenInput> {
            override fun process(input: String): Result<SignInWithMinecraftFailure, GetMicrosoftTokenInput> {
                return Result.success(GetMicrosoftTokenInput(
                    code = input,
                    clientId = getMicrosoftClientId(),
                    clientSecret = getMicrosoftClientSecret(),
                    env = getEnvironment(),
                    host = getHost()
                ))
            }
        })
        .pipe(GetMicrosoftTokenStep)
        .pipe(GetXboxProfileStep)
        .pipe(GetXstsToken)
        .pipe(GetMinecraftToken)
        .pipe(GetMinecraftProfileStep)
        .pipe(CreateUserIfNotExistsStep(usersApi))
        .pipe(CreateTokenStep)
        .pipe(AddCookieStep(response.cookies, getHost() ?: "false"))
        .execute(Unit)

    when(result) {
        is Result.Success -> respondRedirect("/")
        is Result.Failure -> respondRedirect(result.error.toRedirect().url)
    }
}

suspend fun ApplicationCall.handleGetSignOut() {
    response.cookies.removeToken(getHost() ?: "localhost")
    respondRedirect("/", permanent = false)
}

private fun ApplicationCall.getSignInUrl(): String {
    return if (getEnvironment() == Test) {
        "/auth/oidc/test-redirect"
    } else if (getSkipMicrosoftSignIn().lowercase() != "true") {
        getMicrosoftSignInUrl()
    } else {
        "/auth/oidc/local-redirect"
    }
}

private fun ApplicationCall.getMicrosoftSignInUrl(): String {
    val clientId = getMicrosoftClientId()
    val env = getEnvironment()
    val host = getHost()
    val redirectUrl =
        if (env == Local) "http://localhost:8080/auth/oidc/microsoft-redirect"
        else "https://$host/auth/oidc/microsoft-redirect"
    return "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?response_type=code&scope=openid,XboxLive.signin&client_id=$clientId&redirect_uri=$redirectUrl"
}