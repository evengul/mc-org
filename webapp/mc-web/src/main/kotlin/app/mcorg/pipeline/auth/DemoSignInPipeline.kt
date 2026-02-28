package app.mcorg.pipeline.auth

import app.mcorg.config.AppConfig
import app.mcorg.domain.Production
import app.mcorg.domain.model.user.MinecraftProfile
import app.mcorg.domain.pipeline.pipeline
import app.mcorg.pipeline.auth.commonsteps.AddCookieStep
import app.mcorg.pipeline.auth.commonsteps.CreateTokenStep
import app.mcorg.pipeline.auth.commonsteps.CreateUserIfNotExistsStep
import app.mcorg.pipeline.auth.commonsteps.UpdateLastSignInStep
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.utils.getHost
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlin.random.Random

suspend fun ApplicationCall.handleDemoSignIn() {
    val demoUsername = if (AppConfig.env == Production) {
        AppConfig.demoUser
    } else when(val requestedUsername = request.queryParameters["username"]?.trim()) {
        null, "" -> AppConfig.demoUser
        "random" -> "DemoUser_${Random.nextInt(1000, 9999)}"
        else -> requestedUsername
    }
    val demoUuid = "${demoUsername}-uuid"
    val redirectPath = parameters["redirect_to"] ?: "/"

    pipeline(
        onSuccess = { respondRedirect(redirectPath) },
        onFailure = { error: AppFailure ->
            when (error) {
                is AppFailure.AuthError.MissingToken -> respondRedirect("/auth/sign-in")
                is AppFailure.Redirect -> respondRedirect(error.toUrl())
                is AppFailure.AuthError.ConvertTokenError -> respondRedirect(error.toRedirect().toUrl())
                is AppFailure.AuthError.CouldNotCreateToken -> respondRedirect("/auth/sign-out?error=token_creation_failed")
                else -> respondRedirect("/auth/sign-out?error=${error.javaClass.simpleName}")
            }
        }
    ) {
        val profile = MinecraftProfile(uuid = demoUuid, username = demoUsername, isDemoUser = true)
        val tokenProfile = CreateUserIfNotExistsStep.run(profile)
        val token = CreateTokenStep.run(tokenProfile)
        AddCookieStep(response.cookies, getHost() ?: "false").run(token)
        UpdateLastSignInStep.run(demoUsername)
    }
}
