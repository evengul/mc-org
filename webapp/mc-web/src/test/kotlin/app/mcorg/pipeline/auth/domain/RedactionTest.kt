package app.mcorg.pipeline.auth.domain

import app.mcorg.logging.redacted
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.webhook.CreateWebhookSubscriptionInput
import app.mcorg.webhook.DueDelivery
import app.mcorg.webhook.WebhookSubscription
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * MCO-340. Every type carrying a token, a signing secret, or a verbatim upstream body must have a
 * `toString()` that omits it, so that a future `logger.debug("$thing")` is safe by construction.
 *
 * Each case seeds a distinctive value and asserts it does not survive rendering.
 */
class RedactionTest {

    private val secret = "SEEDED-SECRET-VALUE-do-not-log-me"

    private fun assertRedacted(rendered: String, vararg alsoAbsent: String) {
        assertFalse(rendered.contains(secret), "secret survived toString(): $rendered")
        alsoAbsent.forEach {
            assertFalse(rendered.contains(it), "'$it' survived toString(): $rendered")
        }
        assertContains(rendered, "redacted", message = "expected the redaction marker in: $rendered")
    }

    @Test
    fun `redacted keeps the length and nothing else`() {
        assertEquals("<redacted:5>", redacted("abcde"))
        assertEquals("<redacted:empty>", redacted(""))
        assertEquals("<redacted:null>", redacted(null))
    }

    @Test
    fun `MicrosoftAccessTokenResponse hides both tokens`() {
        val rendered = MicrosoftAccessTokenResponse(
            tokenType = "Bearer",
            scope = "XboxLive.signin",
            expiresIn = 3600,
            extExpiresIn = 3600,
            accessToken = secret,
            idToken = "SEEDED-ID-TOKEN",
        ).toString()

        assertRedacted(rendered, "SEEDED-ID-TOKEN")
        // Non-secret fields stay, or the redaction has cost us the debuggability it was meant to keep.
        assertContains(rendered, "Bearer")
    }

    @Test
    fun `XboxTokenResponse hides the token and the user hash`() {
        val rendered = XboxTokenResponse(
            issueInstant = "2026-08-15T00:00:00Z",
            notAfter = "2026-08-16T00:00:00Z",
            token = secret,
            displayClaims = DisplayClaims(listOf(Uhs("SEEDED-USER-HASH"))),
        ).toString()

        // The user hash is nested inside DisplayClaims -> Uhs, so this also proves the nested
        // type's own toString() is doing its job.
        assertRedacted(rendered, "SEEDED-USER-HASH")
        assertContains(rendered, "2026-08-15T00:00:00Z")
    }

    @Test
    fun `Uhs hides the user hash on its own`() {
        assertRedacted(Uhs(secret).toString())
    }

    @Test
    fun `MinecraftTokenResponse hides the access token but keeps the username`() {
        val rendered = MinecraftTokenResponse(
            username = "TestPlayer",
            accessToken = secret,
            expiresIn = 86400,
            tokenType = "Bearer",
        ).toString()

        assertRedacted(rendered)
        assertContains(rendered, "TestPlayer")
    }

    @Test
    fun `TokenData hides both the token and its hash`() {
        assertRedacted(TokenData(token = secret, hash = "SEEDED-HASH").toString(), "SEEDED-HASH")
    }

    @Test
    fun `WebhookSubscription hides the signing secret but keeps the callback url`() {
        val rendered = WebhookSubscription(
            id = 1,
            worldId = 2,
            callbackUrl = "https://example.com/hook",
            secret = secret,
            eventFilter = listOf("*"),
            active = true,
            consecutiveFailures = 0,
        ).toString()

        assertRedacted(rendered)
        assertContains(rendered, "https://example.com/hook")
    }

    @Test
    fun `DueDelivery hides the secret and the payload`() {
        val rendered = DueDelivery(
            id = 1L,
            subscriptionId = 2,
            callbackUrl = "https://example.com/hook",
            secret = secret,
            eventType = "project_created",
            payload = """{"project_name":"SEEDED-PROJECT-NAME"}""",
            attempts = 0,
        ).toString()

        // The payload is a serialised SeamEvent — user-authored content, not a credential, but
        // covered by the same rule (documentation/logging.md).
        assertRedacted(rendered, "SEEDED-PROJECT-NAME")
        assertContains(rendered, "project_created")
    }

    @Test
    fun `CreateWebhookSubscriptionInput hides the secret`() {
        val rendered = CreateWebhookSubscriptionInput(
            worldId = 1,
            callbackUrl = "https://example.com/hook",
            secret = secret,
            eventFilterJson = """["*"]""",
            metadataJson = "{}",
        ).toString()

        assertRedacted(rendered)
    }

    @Test
    fun `HttpError hides the upstream body`() {
        // The case that was already load-bearing: cli/IngestServerFiles interpolates this type
        // into a log line.
        val rendered = AppFailure.ApiError.HttpError(
            statusCode = 400,
            body = """{"error":"invalid_grant","error_description":"$secret"}""",
        ).toString()

        assertFalse(rendered.contains(secret), "upstream body survived toString(): $rendered")
        assertFalse(rendered.contains("invalid_grant"), "upstream body survived toString(): $rendered")
        assertContains(rendered, "400")
    }

    @Test
    fun `HttpError with no body renders zero length rather than null`() {
        assertContains(AppFailure.ApiError.HttpError(statusCode = 500).toString(), "<0 chars>")
    }
}
