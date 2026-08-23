package app.mcorg.presentation.security

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * MCO-352 — the open-redirect allowlist.
 *
 * Three sinks reached `respondRedirect` from user input: `redirect_to` on the sign-in page,
 * `redirect_to` on the unauthenticated demo endpoint, and the OAuth `state` that returns through
 * a genuine Microsoft authorize URL.
 */
class SafeRedirectTest {

    @Nested
    inner class Accepts {

        @Test
        fun `an ordinary relative path`() {
            assertEquals("/worlds/4/projects", safeRedirectPath("/worlds/4/projects"))
        }

        @Test
        fun `a path with a query string`() {
            assertEquals("/ideas?category=FARM", safeRedirectPath("/ideas?category=FARM"))
        }

        @Test
        fun `the site root`() {
            assertEquals("/", safeRedirectPath("/"))
        }

        @Test
        fun `a path containing a colon after the first segment`() {
            // Legal in a path; only a colon in the *first* segment could read as a scheme.
            assertEquals("/worlds/a:b", safeRedirectPath("/worlds/a:b"))
        }
    }

    @Nested
    inner class Rejects {

        @Test
        fun `an absolute URL`() {
            assertEquals("/worlds", safeRedirectPath("https://evil.example"))
        }

        @Test
        fun `a scheme-relative URL`() {
            // The one that catches people out: browsers resolve "//host" as absolute.
            assertEquals("/worlds", safeRedirectPath("//evil.example"))
        }

        @Test
        fun `a javascript URL`() {
            assertEquals("/worlds", safeRedirectPath("javascript:alert(1)"))
        }

        @Test
        fun `a data URL`() {
            assertEquals("/worlds", safeRedirectPath("data:text/html,<script>alert(1)</script>"))
        }

        @Test
        fun `backslash variants browsers normalise to a double slash`() {
            assertEquals("/worlds", safeRedirectPath("\\\\evil.example"))
            assertEquals("/worlds", safeRedirectPath("/\\evil.example"))
        }

        @Test
        fun `a value carrying a newline that could split the header`() {
            assertEquals("/worlds", safeRedirectPath("/worlds\r\nSet-Cookie: a=b"))
        }

        @Test
        fun `null blank and whitespace`() {
            assertEquals("/worlds", safeRedirectPath(null))
            assertEquals("/worlds", safeRedirectPath(""))
            assertEquals("/worlds", safeRedirectPath("   "))
        }

        @Test
        fun `a bare host with no scheme`() {
            assertEquals("/worlds", safeRedirectPath("evil.example/path"))
        }
    }

    @Nested
    inner class Fallback {

        @Test
        fun `is caller-chosen`() {
            // The demo and OAuth sinks pass "/" so MCO-352 changes what is refused, not where a
            // successful sign-in lands.
            assertEquals("/", safeRedirectPath("https://evil.example", fallback = "/"))
            assertEquals("/", safeRedirectPath(null, fallback = "/"))
        }
    }
}
