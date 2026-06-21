package app.mcorg.presentation.plugins

import app.mcorg.config.AppConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond
import java.security.MessageDigest

/** Header carrying the shared secret for the machine-facing webhook admin endpoints. */
const val WEBHOOK_ADMIN_SECRET_HEADER = "X-Seam-Admin-Secret"

/**
 * Route-scoped gate for the webhook admin endpoints (`/integrations/webhooks`). Fails closed: if no
 * `WEBHOOK_ADMIN_SECRET` is configured the endpoints are inert (503). Otherwise the request's
 * [WEBHOOK_ADMIN_SECRET_HEADER] must match it (constant-time compare) or the call is rejected 401.
 *
 * These endpoints are JWT-exempt (see AuthPlugin's allowlist); this shared secret is their only gate.
 */
val WebhookAdminAuthPlugin = createRouteScopedPlugin("WebhookAdminAuthPlugin") {
    onCall { call ->
        val configured = AppConfig.webhookAdminSecret
        if (configured.isNullOrBlank()) {
            call.respond(HttpStatusCode.ServiceUnavailable, "Webhook admin endpoint is not configured")
            return@onCall
        }
        val provided = call.request.headers[WEBHOOK_ADMIN_SECRET_HEADER]
        if (provided == null || !constantTimeEquals(provided, configured)) {
            call.respond(HttpStatusCode.Unauthorized, "Invalid or missing admin secret")
        }
    }
}

private fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
