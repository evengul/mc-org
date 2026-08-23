package app.mcorg.presentation.plugins

import app.mcorg.config.AppConfig
import app.mcorg.domain.Production
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.conditionalheaders.*

/**
 * Content Security Policy (MCO-356).
 *
 * **Read the `'unsafe-inline'` and `'unsafe-eval'` before assuming this stops XSS.** The app emits
 * inline `<script>` blocks (`Alert.kt`), 35 inline `on*` handlers, and htmx attributes that are
 * eval'd — so a policy without both would break the UI on the first page load. What this policy
 * therefore buys is control of *origins*: an injected `<script src>` cannot pull from an
 * attacker's host, `base-uri` cannot be repointed, forms cannot post off-site, and nothing can be
 * framed. It does **not** contain the two latent injection sinks MCO-356 names.
 *
 * Getting there means moving the inline JavaScript into files and setting
 * `htmx.config.allowEval = false` — tracked as its own issue, with dropping these two keywords as
 * the acceptance criterion. Until then this is origin control, and should be described as such.
 *
 * `cdn.jsdelivr.net` is here for the two SRI-pinned HTMX scripts in `Layout.kt`. Everything else
 * is served from this origin, including fonts and icons — icons are inline SVG, not fetched.
 */
private val CONTENT_SECURITY_POLICY = listOf(
    "default-src 'self'",
    // 'unsafe-eval' is here for htmx, not for us. Its `hx-on:` handlers and `hx-vals="js:..."`
    // expressions are compiled with eval(), so a policy without it does not merely weaken the
    // page — it silently breaks every modal, inline-edit and search-as-you-type in the app.
    // Confirmed against htmx's own CSP documentation, which offers `htmx.config.allowEval =
    // false` as the way out and is explicit that doing so disables exactly those features.
    "script-src 'self' https://cdn.jsdelivr.net 'unsafe-inline' 'unsafe-eval'",
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data:",
    "font-src 'self'",
    "connect-src 'self'",
    // The modern spelling of X-Frame-Options; both are sent, since neither covers every browser
    // a hobby product's users might bring.
    "frame-ancestors 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "object-src 'none'",
).joinToString("; ")

/**
 * A year, with subdomains, and preload-eligible. Production only: sending HSTS from a local
 * `http://localhost` run would pin the browser to HTTPS for a host that does not serve it, and
 * the fix for that is clearing site data rather than editing code.
 */
private const val STRICT_TRANSPORT_SECURITY = "max-age=31536000; includeSubDomains"

/**
 * Response headers applied to everything (MCO-356).
 *
 * `configureHTTP` previously installed only caching and conditional headers — no CSP, no HSTS, no
 * nosniff, and framing denied on exactly one route. `force_https` in fly.toml covers transport,
 * but nothing covered the browser-side behaviours.
 */
private fun Application.installSecurityHeaders() {
    intercept(ApplicationCallPipeline.Plugins) {
        val headers = call.response.headers
        headers.append("Content-Security-Policy", CONTENT_SECURITY_POLICY, safeOnly = false)
        headers.append("X-Content-Type-Options", "nosniff", safeOnly = false)
        headers.append("X-Frame-Options", "DENY", safeOnly = false)
        // Send the path to same-origin navigations, only the origin cross-site. Query strings on
        // this app can carry redirect targets and user codes (documentation/logging.md).
        headers.append("Referrer-Policy", "strict-origin-when-cross-origin", safeOnly = false)
        if (AppConfig.env == Production) {
            headers.append(HttpHeaders.StrictTransportSecurity, STRICT_TRANSPORT_SECURITY, safeOnly = false)
        }
    }
}

fun Application.configureHTTP() {
    installSecurityHeaders()

    if (AppConfig.env == Production) {
        install(CachingHeaders) {
            options { _, outgoingContent ->
                when (outgoingContent.contentType?.withoutParameters()) {
                    ContentType.Text.CSS -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60))
                    ContentType.Text.JavaScript -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 15 * 60))
                    ContentType.Text.Xml -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60))
                    ContentType.Font.Any -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60))
                    else -> null
                }
            }
        }
    } else {
        install(CachingHeaders) {
            options { _, outgoingContent ->
                when (outgoingContent.contentType?.withoutParameters()) {
                    ContentType.Text.Xml -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60))
                    ContentType.Font.Any -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60))
                    else -> null
                }
            }
        }
    }
    install(ConditionalHeaders)
}
