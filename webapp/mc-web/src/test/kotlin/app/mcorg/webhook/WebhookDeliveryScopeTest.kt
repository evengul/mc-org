package app.mcorg.webhook

import app.mcorg.domain.Env
import app.mcorg.domain.Local
import app.mcorg.domain.Production
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import app.mcorg.domain.Test as TestEnv

class WebhookDeliveryScopeTest {

    private val base = "https://seam-discord.example.dev"
    private val secret = "this-environments-secret"

    private fun allows(
        callbackUrl: String,
        storedSecret: String = secret,
        env: Env = Local,
        seamDiscordUrl: String? = base,
        sharedSecret: String? = secret,
    ) = WebhookDeliveryScope.allows(env, seamDiscordUrl, sharedSecret, callbackUrl, storedSecret)

    @Test
    fun `production delivers to any subscription, configured or not`() {
        assertTrue(allows("https://elsewhere.example.com/hook", "other", env = Production, seamDiscordUrl = null, sharedSecret = null))
    }

    @Test
    fun `outside production, a subscription this app created is delivered`() {
        assertTrue(allows("$base/seam-events/123"))
        assertTrue(allows("$base/seam-events/123", env = TestEnv))
        assertTrue(allows("$base/seam-events/123", seamDiscordUrl = "$base/"))
    }

    @Test
    fun `outside production, a callback outside SEAM_DISCORD_URL is refused`() {
        assertFalse(allows("https://seam-discord.even-gultvedt.workers.dev/seam-events/123"))
    }

    @Test
    fun `a host that only starts with the configured base is refused`() {
        assertFalse(allows("https://seam-discord.example.dev.attacker.com/seam-events/123"))
        assertFalse(allows(base))
    }

    @Test
    fun `a copied subscription under the configured base but with another secret is refused`() {
        assertFalse(allows("$base/seam-events/123", storedSecret = "production-secret"))
    }

    @Test
    fun `outside production, nothing is delivered while either setting is unset`() {
        assertFalse(allows("$base/seam-events/123", seamDiscordUrl = null))
        assertFalse(allows("$base/seam-events/123", seamDiscordUrl = " "))
        assertFalse(allows("$base/seam-events/123", sharedSecret = null))
        assertFalse(allows("$base/seam-events/123", sharedSecret = ""))
    }
}
