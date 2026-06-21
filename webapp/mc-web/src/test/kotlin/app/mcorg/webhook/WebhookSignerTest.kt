package app.mcorg.webhook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WebhookSignerTest {

    @Test
    fun `matches a known HMAC-SHA256 vector and is prefixed`() {
        // Well-known vector: HMAC-SHA256("key", "The quick brown fox jumps over the lazy dog").
        assertEquals(
            "sha256=f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
            WebhookSigner.sign("key", "The quick brown fox jumps over the lazy dog"),
        )
    }

    @Test
    fun `produces 64 lowercase hex chars after the prefix`() {
        val sig = WebhookSigner.sign("secret", """{"event_type":"project_created"}""")
        val hex = sig.removePrefix("sha256=")
        assertEquals(64, hex.length)
        assertTrue(hex.all { it in "0123456789abcdef" }, "expected lowercase hex, got $hex")
    }

    @Test
    fun `is deterministic but varies with secret and body`() {
        val body = """{"event_type":"task_toggled"}"""
        assertEquals(WebhookSigner.sign("s", body), WebhookSigner.sign("s", body))
        assertNotEquals(WebhookSigner.sign("s1", body), WebhookSigner.sign("s2", body))
        assertNotEquals(WebhookSigner.sign("s", body), WebhookSigner.sign("s", body + " "))
    }
}
