package app.mcorg.webhook

import app.mcorg.config.AppConfig
import app.mcorg.config.Database
import app.mcorg.domain.Env
import app.mcorg.domain.Production
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.event.ProjectCreated
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.mcorg.domain.Test as TestEnv

/**
 * What a non-production app does with the subscriptions it finds in its database. Every such
 * database is a fork of production, so it holds production's subscriptions and, at fork time,
 * production's pending outbox rows (MCO-592). The default [WebhookDeliveryPoller] is used on
 * purpose: the scope it reads from [AppConfig] is the thing under test.
 */
@Tag("database")
@WireMockTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class WebhookDeliveryScopeIT : WithUser() {

    private val consumer = WebhookFanoutConsumer()
    private val poller = WebhookDeliveryPoller()
    private val sharedSecret = "this-environments-secret"

    private lateinit var savedEnv: Env
    private var savedDiscordUrl: String? = null
    private var savedSharedSecret: String? = null

    @BeforeEach
    fun setUp(): Unit = runBlocking {
        savedEnv = AppConfig.env
        savedDiscordUrl = AppConfig.seamDiscordUrl
        savedSharedSecret = AppConfig.webhookSharedSecret
        DatabaseSteps.update<Unit>(sql = SafeSQL.delete("DELETE FROM webhook_deliveries"), parameterSetter = { _, _ -> }).process(Unit)
        DatabaseSteps.update<Unit>(sql = SafeSQL.delete("DELETE FROM webhook_subscriptions"), parameterSetter = { _, _ -> }).process(Unit)
        Unit
    }

    @AfterEach
    fun restoreConfig() {
        AppConfig.env = savedEnv
        AppConfig.seamDiscordUrl = savedDiscordUrl
        AppConfig.webhookSharedSecret = savedSharedSecret
    }

    private fun configure(env: Env, seamDiscordUrl: String?) {
        AppConfig.env = env
        AppConfig.seamDiscordUrl = seamDiscordUrl
        AppConfig.webhookSharedSecret = sharedSecret
    }

    @Test
    fun `a fork posts nothing to a callback outside SEAM_DISCORD_URL, and its rows do not keep the poller awake`(wm: WireMockRuntimeInfo) {
        configure(TestEnv, seamDiscordUrl = "https://seam-discord.invalid")
        val worldId = createWorld("scope-outside")
        val subId = insertSubscription(worldId, wm.httpBaseUrl + "/seam-events/123", sharedSecret)
        stubAccept(wm)

        fanOutAndPoll(worldId)

        assertNoPosts(wm)
        assertEquals("FAILED" to 0, deliveryStatus(subId), "refused without spending an attempt")
        assertEquals(0 to true, subscriptionHealth(subId), "a refusal is not the subscription failing")
        assertNull(runBlocking { WebhookStore.findNextScheduledDeliveryAt() }, "nothing left to wake for")
    }

    @Test
    fun `a pending row copied from production's outbox is not delivered again`(wm: WireMockRuntimeInfo) {
        // The worktree points SEAM_DISCORD_URL at the real Worker: the copied subscription matches
        // on URL, and only its secret — production's — tells it apart.
        configure(TestEnv, seamDiscordUrl = wm.httpBaseUrl)
        val worldId = createWorld("scope-copied")
        val subId = insertSubscription(worldId, wm.httpBaseUrl + "/seam-events/123", "production-secret")
        insertPendingDelivery(subId)
        stubAccept(wm)

        runBlocking { poller.pollOnce(System.currentTimeMillis()) }

        assertNoPosts(wm)
        assertEquals("FAILED" to 0, deliveryStatus(subId))
    }

    @Test
    fun `with SEAM_DISCORD_URL unset, a non-production app delivers nothing`(wm: WireMockRuntimeInfo) {
        configure(TestEnv, seamDiscordUrl = null)
        val worldId = createWorld("scope-unset")
        val subId = insertSubscription(worldId, wm.httpBaseUrl + "/seam-events/123", sharedSecret)
        stubAccept(wm)

        fanOutAndPoll(worldId)

        assertNoPosts(wm)
        assertEquals("FAILED" to 0, deliveryStatus(subId))
    }

    @Test
    fun `a subscription this app created is still delivered outside production`(wm: WireMockRuntimeInfo) {
        configure(TestEnv, seamDiscordUrl = wm.httpBaseUrl)
        val worldId = createWorld("scope-own")
        val subId = insertSubscription(worldId, wm.httpBaseUrl + "/seam-events/123", sharedSecret)
        stubAccept(wm)

        fanOutAndPoll(worldId)

        assertEquals(1, posts(wm))
        assertEquals("DELIVERED" to 0, deliveryStatus(subId))
    }

    @Test
    fun `production delivers to every subscription`(wm: WireMockRuntimeInfo) {
        configure(Production, seamDiscordUrl = null)
        val worldId = createWorld("scope-production")
        val subId = insertSubscription(worldId, wm.httpBaseUrl + "/seam-events/123", "any-secret")
        stubAccept(wm)

        fanOutAndPoll(worldId)

        assertEquals(1, posts(wm))
        assertEquals("DELIVERED" to 0, deliveryStatus(subId))
    }

    private fun stubAccept(wm: WireMockRuntimeInfo) {
        wm.wireMock.register(WireMock.post(WireMock.urlPathMatching("/seam-events/.*")).willReturn(WireMock.aResponse().withStatus(202)))
    }

    private fun posts(wm: WireMockRuntimeInfo): Int =
        wm.wireMock.find(WireMock.postRequestedFor(WireMock.urlPathMatching("/.*"))).size

    private fun assertNoPosts(wm: WireMockRuntimeInfo) = assertEquals(0, posts(wm), "expected no HTTP request")

    private fun fanOutAndPoll(worldId: Int) = runBlocking {
        consumer.handle(ProjectCreated(worldId, user.id, Instant.now(), 1, "Iron Farm", ProjectType.REDSTONE))
        poller.pollOnce(System.currentTimeMillis())
    }

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name, "test", MinecraftVersion.fromString("1.21.4"))
        )
        (result as Result.Success).value
    }

    private fun insertSubscription(worldId: Int, url: String, secret: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                """
                INSERT INTO webhook_subscriptions (world_id, callback_url, secret, event_filter)
                VALUES (?, ?, ?, '["*"]'::jsonb)
                RETURNING id
                """.trimIndent()
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, worldId)
                statement.setString(2, url)
                statement.setString(3, secret)
            },
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun insertPendingDelivery(subId: Int) {
        Database.getConnection().use { conn ->
            conn.prepareStatement(
                "INSERT INTO webhook_deliveries (subscription_id, event_type, payload) VALUES (?, 'project_created', '{}'::jsonb)"
            ).use { st ->
                st.setInt(1, subId)
                st.executeUpdate()
            }
        }
    }

    private fun deliveryStatus(subId: Int): Pair<String, Int> =
        Database.getConnection().use { conn ->
            conn.prepareStatement("SELECT status, attempts FROM webhook_deliveries WHERE subscription_id = ?").use { st ->
                st.setInt(1, subId)
                st.executeQuery().use { rs ->
                    assertTrue(rs.next(), "expected a delivery row for subscription $subId")
                    rs.getString("status") to rs.getInt("attempts")
                }
            }
        }

    /** (consecutive_failures, active) for [subId]. */
    private fun subscriptionHealth(subId: Int): Pair<Int, Boolean> =
        Database.getConnection().use { conn ->
            conn.prepareStatement("SELECT consecutive_failures, active FROM webhook_subscriptions WHERE id = ?").use { st ->
                st.setInt(1, subId)
                st.executeQuery().use { rs ->
                    assertTrue(rs.next(), "expected subscription $subId")
                    rs.getInt("consecutive_failures") to rs.getBoolean("active")
                }
            }
        }
}
