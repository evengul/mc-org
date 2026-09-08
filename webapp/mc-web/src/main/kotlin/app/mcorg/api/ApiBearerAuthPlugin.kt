package app.mcorg.api

import app.mcorg.config.AppConfig
import app.mcorg.domain.Production
import app.mcorg.pipeline.Result
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.util.AttributeKey

private val API_USER_ID_KEY = AttributeKey<Int>("apiUserId")
private val API_TOKEN_HASH_KEY = AttributeKey<String>("apiTokenHash")

/** The authenticated user id for a bearer-gated `/api/v1` call. */
fun ApplicationCall.getApiUserId(): Int = attributes[API_USER_ID_KEY]

/** The hash of the presented bearer token (used e.g. to revoke it). */
fun ApplicationCall.getApiTokenHash(): String = attributes[API_TOKEN_HASH_KEY]

/**
 * Route-scoped bearer gate for the mod-facing JSON API (MCO-236). Reads `Authorization: Bearer
 * <token>`, hashes it (SHA-256), and looks up a live [api_token] row (not revoked, not expired).
 * On success it stores the resolved user id (and token hash) on the call and best-effort bumps
 * `last_used_at`. Fails closed with a JSON 401 on any missing/invalid/expired/revoked token.
 *
 * Modelled on [app.mcorg.presentation.plugins.MachineEndpointAuthPlugin]; the `/api/v1` prefix is
 * JWT-exempt (see AuthPlugin's allowlist) so this is the only gate on these routes.
 */
val ApiBearerAuthPlugin = createRouteScopedPlugin("ApiBearerAuthPlugin") {
    onCall { call ->
        val header = call.request.header("Authorization")
        val token = header?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substring(7)?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (token == null) {
            call.respondApiError(HttpStatusCode.Unauthorized, "invalid_token", "Missing or malformed bearer token")
            return@onCall
        }
        val hash = ApiCrypto.sha256Hex(token)
        when (val lookup = LookupApiTokenUserStep.process(hash)) {
            is Result.Success -> {
                val userId = lookup.value
                // Ban gate: the API is mounted outside the HTML app's BannedPlugin, so enforce the
                // same global ban here (shared lookup + cache). Banned accounts get 403, not 401.
                if ((IsUserBannedStep.process(userId) as? Result.Success)?.value == true) {
                    call.respondApiError(HttpStatusCode.Forbidden, "forbidden", "This account is banned")
                    return@onCall
                }
                call.attributes.put(API_USER_ID_KEY, userId)
                call.attributes.put(API_TOKEN_HASH_KEY, hash)
                // Best-effort; a failed touch must not reject an otherwise valid request.
                TouchApiTokenStep.process(hash)
            }
            is Result.Failure -> {
                call.respondApiError(HttpStatusCode.Unauthorized, "invalid_token", "Invalid, expired, or revoked token")
            }
        }
    }
}

private val REPORTER_WORLD_ID_KEY = AttributeKey<Int>("reporterWorldId")
private val REPORTER_TOKEN_ID_KEY = AttributeKey<Long>("reporterTokenId")

/** The world a reporter token speaks for, or null when the caller authenticated as a player. */
fun ApplicationCall.getReporterWorldId(): Int? = attributes.getOrNull(REPORTER_WORLD_ID_KEY)

/** The reporter token's row id, for stamping last-seen and version after a push. */
fun ApplicationCall.getReporterTokenId(): Long? = attributes.getOrNull(REPORTER_TOKEN_ID_KEY)

/** The authenticated user id, or null when the caller authenticated as a reporter. */
fun ApplicationCall.getApiUserIdOrNull(): Int? = attributes.getOrNull(API_USER_ID_KEY)

/**
 * Gate for the reporter endpoints (MCO-532). Accepts **either** kind of credential, because two
 * genuinely different things sweep containers:
 *
 * - a **reporter token**, which a dedicated server's operator holds. It is world-scoped and
 *   least-privilege by construction (see `V2_68_0`), and its world is fixed by the token.
 * - a **player token**, which is the singleplayer path. There is no server operator in
 *   singleplayer, so the integrated server's sweep pushes as the player. That caller must name the
 *   world and must be a member of it — checked by the handler, which is where world membership can
 *   be checked at all.
 *
 * A reporter token still authenticates *nothing else*: [ApiBearerAuthPlugin] resolves hashes
 * against `api_token`, and a reporter's hash is in `reporter_token`, so every player route rejects
 * it without having to know this plugin exists.
 */
val ApiReporterAuthPlugin = createRouteScopedPlugin("ApiReporterAuthPlugin") {
    onCall { call ->
        val header = call.request.header("Authorization")
        val token = header?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substring(7)?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (token == null) {
            call.respondApiError(HttpStatusCode.Unauthorized, "invalid_token", "Missing or malformed bearer token")
            return@onCall
        }
        val hash = ApiCrypto.sha256Hex(token)

        // Reporter first: it is the common case, and it settles the world without a lookup.
        when (val reporter = LookupReporterTokenStep.process(hash)) {
            is Result.Success -> {
                call.attributes.put(REPORTER_WORLD_ID_KEY, reporter.value.worldId)
                call.attributes.put(REPORTER_TOKEN_ID_KEY, reporter.value.tokenId)
                return@onCall
            }
            is Result.Failure -> Unit
        }

        // Otherwise a player token, with the same ban gate the player routes apply.
        when (val lookup = LookupApiTokenUserStep.process(hash)) {
            is Result.Success -> {
                val userId = lookup.value
                if ((IsUserBannedStep.process(userId) as? Result.Success)?.value == true) {
                    call.respondApiError(HttpStatusCode.Forbidden, "forbidden", "This account is banned")
                    return@onCall
                }
                call.attributes.put(API_USER_ID_KEY, userId)
                call.attributes.put(API_TOKEN_HASH_KEY, hash)
                TouchApiTokenStep.process(hash)
            }
            is Result.Failure ->
                call.respondApiError(HttpStatusCode.Unauthorized, "invalid_token", "Invalid, expired, or revoked token")
        }
    }
}

/**
 * Mirrors the HTML app's [app.mcorg.presentation.plugins.DemoUserPlugin]: in Production, demo users
 * may read but not mutate. Install on the write route group AFTER [ApiBearerAuthPlugin] (it reads the
 * user id that plugin resolves). No-op outside Production and for GET/OPTIONS, matching the web app.
 *
 * **Fails closed on a database error.** This gate used to read
 * `(IsDemoUserStep.process(userId) as? Result.Success)?.value == true`, so a `Result.Failure` — pool
 * exhaustion, a Neon compute suspending, any transient error `DatabaseSteps` explicitly expects —
 * yielded null, which is not `true`, and the write proceeded. The HTML `DemoUserPlugin` it mirrors
 * cannot fail that way because it reads the flag off the JWT, and
 * [app.mcorg.pipeline.world.ValidateWorldMemberRole] is deliberately written to deny on failure.
 * This was the one gate in the app that opened when the database wobbled.
 */
val ApiDemoWriteBlockPlugin = createRouteScopedPlugin("ApiDemoWriteBlockPlugin") {
    onCall { call ->
        if (AppConfig.env != Production) return@onCall
        if (call.request.httpMethod in listOf(HttpMethod.Get, HttpMethod.Options)) return@onCall
        val userId = call.attributes.getOrNull(API_USER_ID_KEY) ?: return@onCall
        when (val demo = IsDemoUserStep.process(userId)) {
            is Result.Success ->
                if (demo.value) {
                    call.respondApiError(HttpStatusCode.Forbidden, "forbidden", "Demo users cannot modify data")
                }
            // Cannot tell whether this is a demo user, so refuse the write rather than allow it.
            is Result.Failure ->
                call.respondApiError(
                    HttpStatusCode.ServiceUnavailable,
                    "unavailable",
                    "Could not verify account status; try again",
                )
        }
    }
}
