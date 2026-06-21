package app.mcorg.webhook

import java.net.InetAddress
import java.net.URI

/**
 * Safety check for a webhook callback URL, run at registration time. The poller will POST to this
 * URL from inside the server, so an unsafe value is an SSRF vector (cloud metadata endpoints,
 * loopback, RFC1918, link-local). This blocks the obvious literal targets and internal hostnames;
 * `ValidationSteps.validateUrl` only checks the scheme.
 *
 * Scope: this catches IP-literal and well-known-name attacks without a live DNS lookup (so it stays
 * hermetic and deterministic). It does NOT defeat a public hostname that *resolves* to a private
 * address or DNS rebinding — that requires resolve-and-block at delivery time and is a follow-up to
 * land before untrusted (per-user `/seam connect`) registration exists. Today registration is
 * shared-secret / operator-only.
 */
object WebhookCallbackUrl {

    private val BLOCKED_HOST_SUFFIXES = listOf(".local", ".internal", ".localhost", ".home.arpa")
    private val BLOCKED_HOSTS = setOf("localhost")

    fun isSafe(rawUrl: String, requireHttps: Boolean): Boolean {
        val url = runCatching { URI.create(rawUrl.trim()).toURL() }.getOrNull() ?: return false

        val scheme = url.protocol.lowercase()
        if (scheme != "http" && scheme != "https") return false
        if (requireHttps && scheme != "https") return false

        val host = url.host?.lowercase()?.trim('[', ']').orEmpty()
        if (host.isBlank()) return false
        if (host in BLOCKED_HOSTS || BLOCKED_HOST_SUFFIXES.any { host.endsWith(it) }) return false

        // Only parse as an address when the host is an IP literal — never resolve a hostname here.
        ipLiteral(host)?.let { address -> if (!address.isPublic()) return false }

        return true
    }

    /** Parse [host] as an IP literal (no DNS), or null if it is a registered hostname. */
    private fun ipLiteral(host: String): InetAddress? {
        val looksNumeric = host.contains(':') || host.all { it.isDigit() || it == '.' }
        return if (looksNumeric) runCatching { InetAddress.getByName(host) }.getOrNull() else null
    }

    private fun InetAddress.isPublic(): Boolean =
        !isLoopbackAddress &&
            !isAnyLocalAddress &&
            !isLinkLocalAddress &&
            !isSiteLocalAddress &&
            !isMulticastAddress &&
            !isUniqueLocalIpv6()

    /** IPv6 unique-local addresses (fc00::/7) — not flagged by the JDK's site-local check. */
    private fun InetAddress.isUniqueLocalIpv6(): Boolean =
        address.size == 16 && (address[0].toInt() and 0xfe) == 0xfc
}
