package app.mcorg.presentation.plugins

import app.mcorg.config.AppConfig
import app.mcorg.domain.Test
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.Base64

private val logger = LoggerFactory.getLogger("PreviewGate")
private const val REALM = "Seam Preview"
private const val GATE_USERNAME = "admin"

// Liveness probes must reach the app without credentials so the platform can tell it is up.
private val UNGATED_PATHS = setOf("/test/ping")

/**
 * HTTP Basic Auth gate for the public preview deployment.
 *
 * Active only when ENV=TEST — the ephemeral Fly/Neon preview that is reachable from the
 * internet and fronts a copy-on-write fork of production data. LOCAL (localhost-only, even
 * with SKIP_MICROSOFT_SIGN_IN) and PRODUCTION (real Microsoft sign-in) are never gated.
 *
 * Fails closed: if PREVIEW_PASSWORD is unset in TEST, every request is rejected rather than
 * leaving the preview open. Sits ahead of routing, so it covers demo sign-in, app routes,
 * and static assets uniformly.
 */
fun Application.configurePreviewGate() {
    if (AppConfig.env != Test) return

    intercept(ApplicationCallPipeline.Plugins) {
        if (call.request.path() in UNGATED_PATHS) return@intercept

        val password = AppConfig.previewPassword
        if (password.isNullOrBlank()) {
            logger.error("PREVIEW_PASSWORD is not set in TEST environment — denying all requests")
            call.respondText("Preview gate is misconfigured.", status = HttpStatusCode.ServiceUnavailable)
            return@intercept finish()
        }

        if (!call.request.header(HttpHeaders.Authorization).isValidBasic(password)) {
            call.response.header(HttpHeaders.WWWAuthenticate, "Basic realm=\"$REALM\", charset=\"UTF-8\"")
            call.respondText("Preview access required.", status = HttpStatusCode.Unauthorized)
            return@intercept finish()
        }
    }
}

/** Validates a `Basic` Authorization header against the pinned username and expected password. */
private fun String?.isValidBasic(expectedPassword: String): Boolean {
    if (this == null || !startsWith("Basic ")) return false
    val decoded = try {
        String(Base64.getDecoder().decode(removePrefix("Basic ").trim()), Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        return false
    }
    val username = decoded.substringBefore(':')
    val password = decoded.substringAfter(':', missingDelimiterValue = "")
    return constantTimeEquals(username, GATE_USERNAME) && constantTimeEquals(password, expectedPassword)
}

private fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
