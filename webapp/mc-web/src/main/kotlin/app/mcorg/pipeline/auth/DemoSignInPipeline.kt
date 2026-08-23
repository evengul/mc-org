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
import app.mcorg.presentation.security.safeRedirectPath
import app.mcorg.presentation.utils.getHost
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.response.*
import org.slf4j.LoggerFactory
import kotlin.random.Random

private val logger = LoggerFactory.getLogger("app.mcorg.pipeline.auth.DemoSignIn")

// Fixed roster of demo identities selectable via ?username= in non-production environments.
// A closed allowlist keeps the caller from steering the UUID lookup (uuid = "$username-uuid")
// onto an arbitrary — or future privileged — existing account. Unknown values fall back to
// the default demo user rather than being honoured.
internal val TEST_DEMO_USERNAMES = setOf("alex", "steve", "lilpebblez")

/**
 * Resolves the demo identity to sign in as, or `null` when demo sign-in is not configured.
 *
 * [defaultUser] is nullable since MCO-333 removed the hardcoded `"evegul"` fallback: with no
 * `DEMO_USER` set there is no identity to fall back *to*, and a sign-in bypass is the last thing
 * that should invent one. Every branch that previously resolved to the default now resolves to
 * null, so the handler fails closed.
 */
internal fun resolveDemoUsername(env: Env, requested: String?, defaultUser: String?): String? {
    // A null defaultUser means DEMO_USER is unset, i.e. demo sign-in is not configured at all.
    // Return before the persona and "random" branches: those mint an identity of their own, and
    // letting them through would leave `?username=alex` working as a sign-in bypass on a
    // deployment that never opted into demo sign-in.
    if (defaultUser == null) return null

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
    // Enforced here, not only in the URL builder (MCO-352). getSignInUrl already refuses to
    // *offer* this path in production and the landing page only renders the Microsoft URL — but
    // the route is registered unconditionally and AuthPlugin lets /oidc through unauthenticated,
    // so nothing stopped a direct GET. Anyone who typed the URL on app.seam.gg got an eight-hour
    // cookie for the shared demo account, with only DemoUserPlugin's `httpMethod !in [Get,
    // Options]` check standing between them and writes.
    //
    // 404 rather than 403: in production this endpoint should not appear to exist.
    if (AppConfig.env == Production) {
        logger.warn("Refused a demo sign-in attempt in production from ${request.origin.remoteHost}")
        respond(HttpStatusCode.NotFound)
        return
    }

    val demoUsername = resolveDemoUsername(AppConfig.env, request.queryParameters["username"], AppConfig.demoUser)
    if (demoUsername == null) {
        // DEMO_USER is unset — the feature is off, not misconfigured mid-request.
        respondRedirect("/auth/sign-in?error=demo_sign_in_not_configured")
        return
    }
    val demoUuid = "${demoUsername}-uuid"
    // fallback "/" rather than the function's "/worlds" default: this endpoint already landed on
    // "/" when no redirect was given, and MCO-352 is about refusing off-origin targets, not about
    // moving where a demo sign-in ends up.
    val redirectPath = safeRedirectPath(parameters["redirect_to"], fallback = "/")

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
