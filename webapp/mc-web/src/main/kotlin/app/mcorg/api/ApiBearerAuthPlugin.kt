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
 * Modelled on [app.mcorg.presentation.plugins.WebhookAdminAuthPlugin]; the `/api/v1` prefix is
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

/**
 * Mirrors the HTML app's [app.mcorg.presentation.plugins.DemoUserPlugin]: in Production, demo users
 * may read but not mutate. Install on the write route group AFTER [ApiBearerAuthPlugin] (it reads the
 * user id that plugin resolves). No-op outside Production and for GET/OPTIONS, matching the web app.
 */
val ApiDemoWriteBlockPlugin = createRouteScopedPlugin("ApiDemoWriteBlockPlugin") {
    onCall { call ->
        if (AppConfig.env != Production) return@onCall
        if (call.request.httpMethod in listOf(HttpMethod.Get, HttpMethod.Options)) return@onCall
        val userId = call.attributes.getOrNull(API_USER_ID_KEY) ?: return@onCall
        if ((IsDemoUserStep.process(userId) as? Result.Success)?.value == true) {
            call.respondApiError(HttpStatusCode.Forbidden, "forbidden", "Demo users cannot modify data")
        }
    }
}
