package app.mcorg.presentation.plugins

import app.mcorg.config.AppConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import org.slf4j.LoggerFactory
import java.net.Inet6Address
import java.net.InetAddress
import java.security.MessageDigest

private val logger = LoggerFactory.getLogger("EdgeOriginGate")

/** Header a Cloudflare Transform Rule adds to every request it forwards to the origin. */
const val EDGE_ORIGIN_HEADER = "X-Seam-Edge"

/** Set by Cloudflare to the address of the client it accepted the connection from. */
const val CF_CONNECTING_IP_HEADER = "CF-Connecting-IP"

// Fly's health checker reaches the machine without passing Cloudflare, so it cannot carry the
// secret. /test/ping is a constant; /test/ready carries its own secret and its callers come in
// through the edge like everyone else.
private val UNGATED_PATHS = setOf("/test/ping")

/**
 * Locks the origin to Cloudflare (MCO-274).
 *
 * app.seam.gg is proxied by Cloudflare, but Fly serves the app to anyone who connects to a Fly
 * edge address and names the host — `mcorg.fly.dev`, or app.seam.gg itself with a pinned address.
 * Removing the fly.dev hostname would not close that, because Fly routes on the name, not the
 * address. A request that skips the edge also skips everything the edge does, and it can send its
 * own `CF-Connecting-IP`, which would let it pick the rate-limit bucket it is counted in.
 *
 * So the edge proves itself: a Transform Rule adds [EDGE_ORIGIN_HEADER] with a shared secret, and
 * when `EDGE_ORIGIN_SECRET` is configured a request without it is refused before routing. Required
 * in PRODUCTION (see ConfigLoader); unset in TEST and LOCAL, which are not behind Cloudflare.
 *
 * Installed ahead of the preview gate and everything else, so nothing — static assets included —
 * is served to a caller that went around the edge.
 */
fun Application.configureEdgeOriginGate() {
    intercept(ApplicationCallPipeline.Plugins) {
        val secret = AppConfig.edgeOriginSecret ?: return@intercept
        if (call.request.path() in UNGATED_PATHS) return@intercept

        val provided = call.request.header(EDGE_ORIGIN_HEADER)
        if (provided == null || !constantTimeEquals(provided, secret)) {
            // Path only: the query string is out of bounds (documentation/logging.md).
            logger.info("Refused a request to {} that did not come through the edge", call.request.path())
            call.respondText("Seam is served at https://app.seam.gg", status = HttpStatusCode.Forbidden)
            return@intercept finish()
        }
    }
}

/**
 * The rate-limit key for the client that made this call.
 *
 * Behind the edge (the gate is on) that is `CF-Connecting-IP`: the peer address is Fly's proxy
 * and `Fly-Client-IP` is a Cloudflare address shared by everyone on the same data centre. Trusting
 * the header is only safe because the gate has already refused anything Cloudflare did not send.
 * Without the gate (LOCAL, the TEST preview) the header is ignored and the peer address is used —
 * `remoteAddress`, never `remoteHost`, which may reverse-resolve and hand back a name.
 *
 * An IPv6 address is reduced to its /64. A subscriber is routinely handed a whole /64, so keying
 * on the full address would let one client rotate through 2^64 keys and never be limited.
 */
fun ApplicationCall.clientAddress(): String {
    val raw = if (AppConfig.edgeOriginSecret != null) {
        request.header(CF_CONNECTING_IP_HEADER) ?: request.origin.remoteAddress
    } else {
        request.origin.remoteAddress
    }
    return rateLimitKeyFor(raw)
}

/**
 * Normalises an address literal into a rate-limit key: IPv4 as-is, IPv6 to its /64 prefix.
 * Anything that is not an address literal is returned unchanged — it is still a stable key, and
 * resolving it as a hostname would put a DNS lookup on the request path.
 */
internal fun rateLimitKeyFor(raw: String): String {
    val address = try {
        // ofLiteral never resolves: a non-literal throws rather than becoming a DNS query.
        InetAddress.ofLiteral(raw.trim())
    } catch (_: IllegalArgumentException) {
        return raw
    }
    if (address !is Inet6Address) return address.hostAddress
    val prefix = address.address.copyOf(16).also { bytes -> bytes.fill(0, fromIndex = 8, toIndex = 16) }
    return InetAddress.getByAddress(prefix).hostAddress + "/64"
}

private fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
