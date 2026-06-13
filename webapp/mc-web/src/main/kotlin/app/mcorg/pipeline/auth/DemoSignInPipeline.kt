package app.mcorg.pipeline.auth

import app.mcorg.config.AppConfig
import app.mcorg.domain.Env
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

// Fixed roster of demo identities selectable via ?username= in non-production environments.
// A closed allowlist keeps the caller from steering the UUID lookup (uuid = "$username-uuid")
// onto an arbitrary — or future privileged — existing account. Unknown values fall back to
// the default demo user rather than being honoured.
internal val TEST_DEMO_USERNAMES = setOf("alex", "steve", "lilpebblez")

internal fun resolveDemoUsername(env: Env, requested: String?, defaultUser: String): String {
    val name = requested?.trim()
    return when {
        env == Production -> defaultUser
        name.isNullOrEmpty() -> defaultUser
        name == "random" -> "DemoUser_${Random.nextInt(1000, 9999)}"
        name == defaultUser || name in TEST_DEMO_USERNAMES -> name
        else -> defaultUser
    }
}

suspend fun ApplicationCall.handleDemoSignIn() {
    val demoUsername = resolveDemoUsername(AppConfig.env, request.queryParameters["username"], AppConfig.demoUser)
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
