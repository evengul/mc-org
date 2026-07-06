package app.mcorg.webhook

import app.mcorg.config.Database
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("database")
@WireMockTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class WebhookDeliveryIT : WithUser() {

    private val consumer = WebhookFanoutConsumer()
    private val poller = WebhookDeliveryPoller()

    /**
     * [WebhookStore.findNextScheduledDeliveryAt] scans the whole outbox (by design: one poller
     * loop drains every world's subscriptions), so the new tests that assert on its global
     * null/non-null result need a clean outbox per test -- the pre-existing tests here only ever
     * assert on rows scoped by an explicit id, so this is safe to add without touching them.
     */
    @BeforeEach
    fun cleanWebhookState(): Unit = runBlocking {
        DatabaseSteps.update<Unit>(sql = SafeSQL.delete("DELETE FROM webhook_deliveries"), parameterSetter = { _, _ -> }).process(Unit)
        DatabaseSteps.update<Unit>(sql = SafeSQL.delete("DELETE FROM webhook_subscriptions"), parameterSetter = { _, _ -> }).process(Unit)
        Unit
    }

    @Test
    fun `delivers a matching event as a signed POST and marks it DELIVERED`(wm: WireMockRuntimeInfo) {
        val worldId = createWorld("wh-deliver")
        val secret = "s3cr3t"
        val subId = insertSubscription(worldId, wm.httpBaseUrl + "/hook", secret, """["project_created"]""")
        wm.wireMock.register(WireMock.post("/hook").willReturn(WireMock.aResponse().withStatus(202)))

        fanOutAndPoll(ProjectCreated(worldId, user.id, Instant.now(), 1, "Iron Farm", ProjectType.REDSTONE))

        val requests = wm.wireMock.find(WireMock.postRequestedFor(WireMock.urlEqualTo("/hook")))
        assertEquals(1, requests.size, "expected exactly one delivery POST")
        val body = requests[0].bodyAsString
        assertEquals("project_created", Json.parseToJsonElement(body).jsonObject["event_type"]!!.jsonPrimitive.content)
        // Signature is computed over the exact bytes POSTed.
        assertEquals(WebhookSigner.sign(secret, body), requests[0].getHeader(WebhookSigner.HEADER))

        assertEquals("DELIVERED" to 0, deliveryStatus(subId))
    }

    @Test
    fun `does not deliver events that do not match the subscription filter`(wm: WireMockRuntimeInfo) {
        val worldId = createWorld("wh-filter")
        insertSubscription(worldId, wm.httpBaseUrl + "/hook", "x", """["task_toggled"]""")
        wm.wireMock.register(WireMock.post("/hook").willReturn(WireMock.aResponse().withStatus(202)))

        fanOutAndPoll(ProjectCreated(worldId, user.id, Instant.now(), 1, "Iron Farm", ProjectType.REDSTONE))

        assertEquals(0, wm.wireMock.find(WireMock.postRequestedFor(WireMock.urlEqualTo("/hook"))).size)
    }

    @Test
    fun `batches multiple due events for one subscription into a single events array`(wm: WireMockRuntimeInfo) {
        val worldId = createWorld("wh-batch")
        insertSubscription(worldId, wm.httpBaseUrl + "/hook", "x", """["*"]""")
        wm.wireMock.register(WireMock.post("/hook").willReturn(WireMock.aResponse().withStatus(202)))

        runBlocking {
            consumer.handle(ProjectCreated(worldId, user.id, Instant.now(), 1, "A", ProjectType.REDSTONE))
            consumer.handle(ProjectCreated(worldId, user.id, Instant.now(), 2, "B", ProjectType.REDSTONE))
            poller.pollOnce(System.currentTimeMillis())
        }

        val requests = wm.wireMock.find(WireMock.postRequestedFor(WireMock.urlEqualTo("/hook")))
        assertEquals(1, requests.size, "two events to one subscription should coalesce into one POST")
        val events = Json.parseToJsonElement(requests[0].bodyAsString).jsonObject["events"]!!.jsonArray
        assertEquals(2, events.size)
    }

    @Test
    fun `reschedules with an attempt bump and records subscription failure on HTTP error`(wm: WireMockRuntimeInfo) {
        val worldId = createWorld("wh-fail")
        val subId = insertSubscription(worldId, wm.httpBaseUrl + "/hook", "x", """["*"]""")
        wm.wireMock.register(WireMock.post("/hook").willReturn(WireMock.aResponse().withStatus(500)))

        fanOutAndPoll(ProjectCreated(worldId, user.id, Instant.now(), 1, "A", ProjectType.REDSTONE))

        // One failed attempt: still PENDING for retry, attempt count bumped.
        assertEquals("PENDING" to 1, deliveryStatus(subId))
        // Subscription health: one consecutive failure, well below the deactivation threshold.
        val (failures, active) = subscriptionHealth(subId)
        assertEquals(1, failures)
        assertTrue(active)
    }

    @Test
    fun `findNextScheduledDeliveryAt is null when the outbox is empty`() = runBlocking {
        // Fresh world, no subscription, no deliveries at all.
        createWorld("wh-empty")
        assertEquals(null, WebhookStore.findNextScheduledDeliveryAt())
    }

    @Test
    fun `findNextScheduledDeliveryAt is the earliest PENDING row across active subscriptions`(wm: WireMockRuntimeInfo) {
        val worldId = createWorld("wh-schedule")
        val laterSubId = insertSubscription(worldId, wm.httpBaseUrl + "/a", "x", """["*"]""")
        val soonerSubId = insertSubscription(worldId, wm.httpBaseUrl + "/b", "x", """["*"]""")
        setNextAttemptAt(insertDelivery(laterSubId), Instant.now().plusSeconds(300))
        val soonerAt = Instant.now().plusSeconds(30)
        setNextAttemptAt(insertDelivery(soonerSubId), soonerAt)

        val next = runBlocking { WebhookStore.findNextScheduledDeliveryAt() }

        assertTrue(next != null && next.isBefore(soonerAt.plusSeconds(1)) && next.isAfter(soonerAt.minusSeconds(1)))
    }

    @Test
    fun `findNextScheduledDeliveryAt ignores rows whose subscription is inactive`(wm: WireMockRuntimeInfo) {
        val worldId = createWorld("wh-inactive-sched")
        val subId = insertSubscription(worldId, wm.httpBaseUrl + "/hook", "x", """["*"]""")
        setNextAttemptAt(insertDelivery(subId), Instant.now().plusSeconds(30))
        deactivateSubscription(subId)

        assertEquals(null, runBlocking { WebhookStore.findNextScheduledDeliveryAt() })
    }

    @Test
    fun `after a failed delivery the schedule reflects the backoff, then quiesces once FAILED`(wm: WireMockRuntimeInfo) {
        val worldId = createWorld("wh-backoff")
        val subId = insertSubscription(worldId, wm.httpBaseUrl + "/hook", "x", """["*"]""")
        wm.wireMock.register(WireMock.post("/hook").willReturn(WireMock.aResponse().withStatus(500)))

        fanOutAndPoll(ProjectCreated(worldId, user.id, Instant.now(), 1, "A", ProjectType.REDSTONE))

        // One failure: rescheduled ~30s out (first-attempt backoff) -- this is the timestamp
        // `awaitNextWake` would wake the loop on, not a fixed 5s tick.
        assertEquals("PENDING" to 1, deliveryStatus(subId))
        val afterFirstFailure = runBlocking { WebhookStore.findNextScheduledDeliveryAt() }
        val expectedAt = Instant.now().plusSeconds(30)
        assertTrue(
            afterFirstFailure != null &&
                afterFirstFailure.isAfter(expectedAt.minusSeconds(5)) &&
                afterFirstFailure.isBefore(expectedAt.plusSeconds(5)),
            "expected the next wake ~30s out, got $afterFirstFailure",
        )

        // Drive it to FAILED without waiting out the real backoff: two more failed attempts exhaust
        // WebhookDeliveryPoller.MAX_ATTEMPTS.
        val ids = listOf(deliveryIdFor(subId))
        runBlocking {
            WebhookStore.failOrReschedule(ids, WebhookDeliveryPoller.MAX_ATTEMPTS, "boom")
            WebhookStore.failOrReschedule(ids, WebhookDeliveryPoller.MAX_ATTEMPTS, "boom")
        }

        assertEquals("FAILED" to 3, deliveryStatus(subId))
        // Nothing left to schedule: the loop can now park indefinitely again.
        assertEquals(null, runBlocking { WebhookStore.findNextScheduledDeliveryAt() })
    }

    // --- helpers -------------------------------------------------------------

    private fun fanOutAndPoll(event: ProjectCreated) = runBlocking {
        consumer.handle(event)
        poller.pollOnce(System.currentTimeMillis())
    }

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name, "test", MinecraftVersion.fromString("1.21.4"))
        )
        (result as Result.Success).value
    }

    private fun insertSubscription(worldId: Int, url: String, secret: String, filterJson: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                """
                INSERT INTO webhook_subscriptions (world_id, callback_url, secret, event_filter)
                VALUES (?, ?, ?, ?::jsonb)
                RETURNING id
                """.trimIndent()
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, worldId)
                statement.setString(2, url)
                statement.setString(3, secret)
                statement.setString(4, filterJson)
            },
        ).process(Unit)
        (result as Result.Success).value
    }

    /** (status, attempts) of the single delivery row for [subId]. */
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

    /** Inserts one PENDING outbox row (due immediately) for [subId] and returns its id. */
    private fun insertDelivery(subId: Int): Long =
        Database.getConnection().use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO webhook_deliveries (subscription_id, event_type, payload)
                VALUES (?, 'project_created', '{}'::jsonb)
                RETURNING id
                """.trimIndent()
            ).use { st ->
                st.setInt(1, subId)
                st.executeQuery().use { rs ->
                    assertTrue(rs.next())
                    rs.getLong("id")
                }
            }
        }

    /** The single delivery row id for [subId] (test setups in this file insert at most one). */
    private fun deliveryIdFor(subId: Int): Long =
        Database.getConnection().use { conn ->
            conn.prepareStatement("SELECT id FROM webhook_deliveries WHERE subscription_id = ?").use { st ->
                st.setInt(1, subId)
                st.executeQuery().use { rs ->
                    assertTrue(rs.next(), "expected a delivery row for subscription $subId")
                    rs.getLong("id")
                }
            }
        }

    private fun setNextAttemptAt(deliveryId: Long, at: Instant) {
        Database.getConnection().use { conn ->
            conn.prepareStatement("UPDATE webhook_deliveries SET next_attempt_at = ? WHERE id = ?").use { st ->
                st.setTimestamp(1, java.sql.Timestamp.from(at))
                st.setLong(2, deliveryId)
                st.executeUpdate()
            }
        }
    }

    private fun deactivateSubscription(subId: Int) {
        Database.getConnection().use { conn ->
            conn.prepareStatement("UPDATE webhook_subscriptions SET active = false WHERE id = ?").use { st ->
                st.setInt(1, subId)
                st.executeUpdate()
            }
        }
    }
}
