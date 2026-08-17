package app.mcorg.presentation.plugins

import app.mcorg.config.AppConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond
import java.security.MessageDigest

/** Header carrying the shared secret for machine-facing endpoints. */
const val MACHINE_SECRET_HEADER = "X-Seam-Admin-Secret"

/**
 * Route-scoped gate for endpoints whose callers are machines rather than signed-in people.
 *
 * Fails closed: if no `WEBHOOK_ADMIN_SECRET` is configured the endpoints are inert (503).
 * Otherwise the request's [MACHINE_SECRET_HEADER] must match it (constant-time compare) or the
 * call is rejected 401.
 *
 * Guards two surfaces, both JWT-exempt via AuthPlugin's allowlist, for both of which this shared
 * secret is the only gate:
 *
 *  - the webhook admin endpoints (`/integrations/webhooks`), its original caller;
 *  - the readiness probe (`/test/ready`), added by MCO-326's review. That one runs `SELECT 1` on
 *    the production pool, and while it was open any stranger could hold Neon's compute awake
 *    around the clock — the same cost failure the liveness/readiness split exists to avoid — as
 *    well as exhaust the pool and drive unbounded ERROR log volume during an outage.
 *
 * One secret for both is deliberate. A second environment variable would have to be threaded
 * through `local.env.example`, `test.env`, `fly.toml` and `configuration.md` to gate an endpoint
 * that currently has no caller at all; when a real second consumer appears with a different trust
 * level, split it then.
 *
 * `WEBHOOK_ADMIN_SECRET` is unset in production today, so `/test/ready` answers 503 until it is
 * configured. That is the intended state: the probe's callers (a deploy smoke test, MCO-344's
 * ingestion alert) do not exist yet either, and an inert endpoint is the right resting position
 * for one nobody calls.
 */
val MachineEndpointAuthPlugin = createRouteScopedPlugin("MachineEndpointAuthPlugin") {
    onCall { call ->
        val configured = AppConfig.webhookAdminSecret
        if (configured.isNullOrBlank()) {
            call.respond(HttpStatusCode.ServiceUnavailable, "This endpoint is not configured")
            return@onCall
        }
        val provided = call.request.headers[MACHINE_SECRET_HEADER]
        if (provided == null || !constantTimeEquals(provided, configured)) {
            call.respond(HttpStatusCode.Unauthorized, "Invalid or missing admin secret")
        }
    }
}

private fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
