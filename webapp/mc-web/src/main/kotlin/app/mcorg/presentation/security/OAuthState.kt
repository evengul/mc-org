package app.mcorg.presentation.security

import app.mcorg.config.AppConfig
import app.mcorg.domain.Local
import io.ktor.server.response.ResponseCookies
import io.ktor.util.date.GMTDate
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

/** Short-lived cookie holding the nonce half of the OAuth `state`. */
const val OAUTH_STATE_COOKIE = "seam_oauth_state"

/**
 * How long a sign-in attempt may sit unfinished. Long enough for a slow Microsoft login including
 * a password reset, short enough that a nonce captured from a browser is not useful later.
 */
private const val OAUTH_STATE_TTL_SECONDS = 15L * 60

private val secureRandom = SecureRandom()

/**
 * Separates the nonce from the redirect path inside `state`. `:` cannot appear in the first
 * segment of a value [safeRedirectPath] accepts, so the split is unambiguous.
 */
private const val STATE_SEPARATOR = ':'

/**
 * Login-CSRF protection for the OAuth callback (MCO-355).
 *
 * `state` carried only the post-login redirect path, and nothing was generated, stored or
 * verified — so the callback accepted any `code` the browser was pointed at. An attacker feeds a
 * victim their *own* authorization code, the victim's browser silently becomes logged into the
 * attacker's account, and anything they create afterwards lands in the attacker's world. Fiddly
 * to execute and low payoff on this product, which is why it is low priority rather than urgent.
 *
 * The nonce lives in a cookie and its twin travels in `state`; the callback requires both to be
 * present and equal. An attacker can choose the `state` on the URL they send, but cannot set a
 * cookie on the victim's browser for this origin, so they cannot make the two agree.
 */
fun newOAuthNonce(): String {
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/** Packs the nonce and the post-login path into a single `state` value. */
fun encodeOAuthState(nonce: String, redirectPath: String): String = "$nonce$STATE_SEPARATOR$redirectPath"

/**
 * Splits a `state` back into nonce and redirect path, or `null` if it is not the expected shape.
 *
 * The path half is deliberately *not* validated here — [safeRedirectPath] owns that, and doing it
 * in one place keeps the two concerns from drifting apart.
 */
fun decodeOAuthState(state: String?): Pair<String, String>? {
    if (state.isNullOrBlank()) return null
    val separator = state.indexOf(STATE_SEPARATOR)
    if (separator <= 0) return null
    val nonce = state.substring(0, separator)
    val redirectPath = state.substring(separator + 1)
    return if (nonce.isBlank()) null else nonce to redirectPath
}

/**
 * Compares two nonces without leaking their contents through timing.
 *
 * Overkill for a value the attacker cannot observe, but this is the same primitive the four
 * shared secrets already use, and reaching for `==` in an auth check is the habit worth avoiding.
 */
fun nonceMatches(fromState: String, fromCookie: String?): Boolean {
    if (fromCookie.isNullOrBlank()) return false
    return MessageDigest.isEqual(
        fromState.toByteArray(Charsets.UTF_8),
        fromCookie.toByteArray(Charsets.UTF_8),
    )
}

/**
 * Stores [nonce] for the duration of one sign-in attempt.
 *
 * `SameSite=Lax`, not `Strict`: the callback arrives as a top-level navigation *from
 * login.microsoftonline.com*, and Strict would withhold the cookie on exactly that hop, breaking
 * every sign-in. Lax still withholds it from cross-site subresource requests, which is what
 * matters here.
 *
 * Host-only — no `Domain` attribute — so it is not shared with subdomains. Unlike the session
 * cookie there is no reason to widen it, and MCO-356 notes that the session cookie's domain is
 * derived partly from the `Referer` header, which is attacker-influenced.
 */
fun ResponseCookies.setOAuthNonce(nonce: String) {
    append(
        name = OAUTH_STATE_COOKIE,
        value = nonce,
        httpOnly = true,
        secure = AppConfig.env != Local,
        path = "/",
        expires = GMTDate(timestamp = Instant.now().plusSeconds(OAUTH_STATE_TTL_SECONDS).toEpochMilli()),
        extensions = mapOf("SameSite" to "Lax"),
    )
}

/** Single-use: the nonce is cleared as soon as the callback has read it, pass or fail. */
fun ResponseCookies.clearOAuthNonce() {
    append(
        name = OAUTH_STATE_COOKIE,
        value = "",
        httpOnly = true,
        secure = AppConfig.env != Local,
        path = "/",
        expires = GMTDate(timestamp = 0),
        extensions = mapOf("SameSite" to "Lax"),
    )
}
