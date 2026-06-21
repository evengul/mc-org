package app.mcorg.webhook

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebhookCallbackUrlTest {

    @Test
    fun `accepts public http and https urls`() {
        assertTrue(WebhookCallbackUrl.isSafe("https://discord.com/api/webhooks/123/abc", requireHttps = false))
        assertTrue(WebhookCallbackUrl.isSafe("http://example.com/hook", requireHttps = false))
    }

    @Test
    fun `rejects non-http schemes and malformed urls`() {
        assertFalse(WebhookCallbackUrl.isSafe("ftp://example.com/x", requireHttps = false))
        assertFalse(WebhookCallbackUrl.isSafe("file:///etc/passwd", requireHttps = false))
        assertFalse(WebhookCallbackUrl.isSafe("not-a-url", requireHttps = false))
        assertFalse(WebhookCallbackUrl.isSafe("", requireHttps = false))
    }

    @Test
    fun `rejects loopback and internal hostnames`() {
        assertFalse(WebhookCallbackUrl.isSafe("http://localhost/hook", requireHttps = false))
        assertFalse(WebhookCallbackUrl.isSafe("http://db.internal/hook", requireHttps = false))
        assertFalse(WebhookCallbackUrl.isSafe("http://service.local/hook", requireHttps = false))
    }

    @Test
    fun `rejects loopback, private, and link-local ip literals`() {
        assertFalse(WebhookCallbackUrl.isSafe("http://127.0.0.1/hook", requireHttps = false))
        assertFalse(WebhookCallbackUrl.isSafe("http://10.0.0.5/hook", requireHttps = false))
        assertFalse(WebhookCallbackUrl.isSafe("http://192.168.1.10/hook", requireHttps = false))
        assertFalse(WebhookCallbackUrl.isSafe("http://172.16.4.4/hook", requireHttps = false))
        assertFalse(WebhookCallbackUrl.isSafe("http://0.0.0.0/hook", requireHttps = false))
        // Cloud metadata endpoint (link-local) — the canonical SSRF target.
        assertFalse(WebhookCallbackUrl.isSafe("http://169.254.169.254/latest/meta-data", requireHttps = false))
        // IPv6 loopback and unique-local.
        assertFalse(WebhookCallbackUrl.isSafe("http://[::1]/hook", requireHttps = false))
        assertFalse(WebhookCallbackUrl.isSafe("http://[fc00::1]/hook", requireHttps = false))
    }

    @Test
    fun `requires https when asked`() {
        assertFalse(WebhookCallbackUrl.isSafe("http://example.com/hook", requireHttps = true))
        assertTrue(WebhookCallbackUrl.isSafe("https://example.com/hook", requireHttps = true))
    }
}
