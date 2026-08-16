package app.mcorg.presentation.plugins

import app.mcorg.config.AppConfig
import app.mcorg.domain.Local
import app.mcorg.domain.Production
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MCO-356 — the security response headers.
 *
 * `configureHTTP` installed only caching and conditional headers: no CSP, no HSTS, no nosniff, and
 * framing denied on exactly one route.
 */
class SecurityHeadersIT {

    @AfterEach
    fun restoreEnv() {
        // One test below sets Production; surefire reuses the JVM (MCO-379).
        AppConfig.env = Local
    }

    private fun headersOf(env: app.mcorg.domain.Env, assertions: (Map<String, String?>) -> Unit) = testApplication {
        AppConfig.env = env
        application { configureHTTP() }
        routing { get("/anything") { call.respondText("ok") } }

        val response = createClient { followRedirects = false }.get("/anything")
        assertEquals(HttpStatusCode.OK, response.status)

        assertions(
            listOf(
                "Content-Security-Policy",
                "X-Content-Type-Options",
                "X-Frame-Options",
                "Referrer-Policy",
                "Strict-Transport-Security",
            ).associateWith { response.headers[it] }
        )
    }

    @Test
    fun `every response carries the baseline headers`() {
        headersOf(Local) { headers ->
            assertEquals("nosniff", headers["X-Content-Type-Options"])
            assertEquals("DENY", headers["X-Frame-Options"])
            assertEquals("strict-origin-when-cross-origin", headers["Referrer-Policy"])
            assertTrue(headers["Content-Security-Policy"] != null, "a CSP should be sent")
        }
    }

    @Test
    fun `the CSP still allows the two SRI-pinned jsDelivr scripts`() {
        // Layout.kt loads htmx and its response-targets extension from jsDelivr with integrity
        // hashes. A CSP that forgot them would break every interactive page in the app.
        headersOf(Local) { headers ->
            val csp = headers["Content-Security-Policy"]!!
            assertTrue(
                "https://cdn.jsdelivr.net" in csp,
                "the CSP must allow the pinned HTMX scripts; was: $csp",
            )
        }
    }

    @Test
    fun `the CSP allows the eval htmx depends on`() {
        // Not an endorsement — a reminder. htmx compiles hx-on: handlers and hx-vals="js:..."
        // with eval(), so dropping 'unsafe-eval' without first moving that JavaScript out breaks
        // every modal, inline edit and search-as-you-type, silently and only in the browser.
        // When the follow-up work lands, this test should be inverted rather than deleted.
        headersOf(Local) { headers ->
            val csp = headers["Content-Security-Policy"]!!
            assertTrue("'unsafe-eval'" in csp, "htmx needs eval until the inline JS moves out; was: $csp")
        }
    }

    @Test
    fun `the CSP denies framing, off-site forms and plugins`() {
        headersOf(Local) { headers ->
            val csp = headers["Content-Security-Policy"]!!
            assertTrue("frame-ancestors 'none'" in csp, "was: $csp")
            assertTrue("form-action 'self'" in csp, "was: $csp")
            assertTrue("object-src 'none'" in csp, "was: $csp")
            assertTrue("base-uri 'self'" in csp, "was: $csp")
        }
    }

    @Test
    fun `HSTS is sent in production only`() {
        // Sending it from a local http listener pins the browser to HTTPS for a host that does
        // not serve it, and the remedy is clearing site data rather than a code change.
        headersOf(Local) { headers ->
            assertNull(headers["Strict-Transport-Security"], "HSTS must not be sent locally")
        }
        headersOf(Production) { headers ->
            val hsts = headers["Strict-Transport-Security"]
            assertTrue(hsts != null && "max-age=31536000" in hsts, "was: $hsts")
        }
    }
}
