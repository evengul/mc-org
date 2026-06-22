package app.mcorg.presentation.templated.settings

import app.mcorg.pipeline.world.settings.buildDiscordCallbackUrl
import app.mcorg.webhook.WebhookSubscription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscordSectionTest {

    private fun sub(id: Int, callbackUrl: String) = WebhookSubscription(
        id = id,
        worldId = 1,
        callbackUrl = callbackUrl,
        secret = "s",
        eventFilter = listOf("*"),
        active = true,
        consecutiveFailures = 0,
    )

    @Test
    fun `parses channel id from a plain seam-events url`() {
        val c = parseDiscordConnection(7, "https://disc.example.com/seam-events/123456789012345678")
        assertEquals(DiscordConnection(7, "123456789012345678", compact = false), c)
    }

    @Test
    fun `parses compact flag from query`() {
        val c = parseDiscordConnection(9, "https://disc.example.com/seam-events/123456789012345678?compact=1")
        assertEquals(DiscordConnection(9, "123456789012345678", compact = true), c)
    }

    @Test
    fun `returns null for a non-discord callback url`() {
        assertNull(parseDiscordConnection(1, "https://example.com/hook"))
        assertNull(parseDiscordConnection(1, "https://disc.example.com/seam-events/not-a-snowflake"))
        assertNull(parseDiscordConnection(1, "https://disc.example.com/seam-events/12"))
    }

    @Test
    fun `discordConnections filters by base url and parses ids`() {
        val base = "https://disc.example.com"
        val subs = listOf(
            sub(1, "$base/seam-events/123456789012345678"),
            sub(2, "$base/seam-events/223456789012345678?compact=1"),
            sub(3, "https://other.example.com/seam-events/323456789012345678"), // wrong base
            sub(4, "$base/hook"), // wrong shape
        )
        val connections = discordConnections(subs, base)
        assertEquals(listOf(1, 2), connections.map { it.subscriptionId })
        assertTrue(connections.first { it.subscriptionId == 2 }.compact)
    }

    @Test
    fun `discordConnections is empty when base is null or blank`() {
        val subs = listOf(sub(1, "https://disc.example.com/seam-events/123456789012345678"))
        assertTrue(discordConnections(subs, null).isEmpty())
        assertTrue(discordConnections(subs, "").isEmpty())
    }

    @Test
    fun `buildDiscordCallbackUrl trims trailing slash and adds compact query`() {
        assertEquals(
            "https://disc.example.com/seam-events/123456789012345678",
            buildDiscordCallbackUrl("https://disc.example.com/", "123456789012345678", compact = false),
        )
        assertEquals(
            "https://disc.example.com/seam-events/123456789012345678?compact=1",
            buildDiscordCallbackUrl("https://disc.example.com", "123456789012345678", compact = true),
        )
    }
}
