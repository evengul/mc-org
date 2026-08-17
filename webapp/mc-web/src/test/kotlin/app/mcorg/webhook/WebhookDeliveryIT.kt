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
import kotlinx.coroutines.async
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

    // --- MCO-357: claiming ---------------------------------------------------------------------

    @Test
    fun `two concurrent polls claim each row exactly once`(wm: WireMockRuntimeInfo) {
        // The duplicate-webhook scenario. Before claiming, both scans returned the same PENDING
        // rows and the row was only mutated after the POST returned, so every delivery would have
        // gone out twice the moment Fly ran a second machine.
        val worldId = createWorld("wh-concurrent")
        val subId = insertSubscription(worldId, wm.httpBaseUrl + "/hook", "x", """["*"]""")
        val ids = (1..20).map { insertDelivery(subId) }.toSet()

        val (first, second) = runBlocking {
            val a = async { WebhookStore.claimDueDeliveries(WebhookDeliveryPoller.MAX_ATTEMPTS) }
            val b = async { WebhookStore.claimDueDeliveries(WebhookDeliveryPoller.MAX_ATTEMPTS) }
            a.await() to b.await()
        }

        val firstIds = first.map { it.id }
        val secondIds = second.map { it.id }

        assertTrue(
            firstIds.intersect(secondIds.toSet()).isEmpty(),
            "the same row was claimed by both polls: ${firstIds.intersect(secondIds.toSet())}",
        )
        assertEquals(
            ids, (firstIds + secondIds).toSet(),
            "every row should be claimed exactly once between the two polls",
        )
    }

    @Test
    fun `a claimed row is invisible to the next poll`(wm: WireMockRuntimeInfo) {
        val worldId = createWorld("wh-claimed-hidden")
        val subId = insertSubscription(worldId, wm.httpBaseUrl + "/hook", "x", """["*"]""")
        insertDelivery(subId)

        val first = runBlocking { WebhookStore.claimDueDeliveries(WebhookDeliveryPoller.MAX_ATTEMPTS) }
        val second = runBlocking { WebhookStore.claimDueDeliveries(WebhookDeliveryPoller.MAX_ATTEMPTS) }

        assertEquals(1, first.size, "the first poll should claim the row")
        assertTrue(second.isEmpty(), "a claimed row must not be handed out again inside the lease")
    }

    @Test
    fun `a stranded claim is reclaimed once its lease expires`(wm: WireMockRuntimeInfo) {
        // A poller killed between claiming and delivering leaves the row IN_FLIGHT with nobody
        // coming back for it. Without reclaim that webhook is simply lost.
        val worldId = createWorld("wh-stranded")
        val subId = insertSubscription(worldId, wm.httpBaseUrl + "/hook", "x", """["*"]""")
        val deliveryId = insertDelivery(subId)

        runBlocking { WebhookStore.claimDueDeliveries(WebhookDeliveryPoller.MAX_ATTEMPTS) }
        assertTrue(runBlocking { WebhookStore.claimDueDeliveries(WebhookDeliveryPoller.MAX_ATTEMPTS) }.isEmpty())

        ageClaim(deliveryId, minutes = 10)

        val reclaimed = runBlocking { WebhookStore.claimDueDeliveries(WebhookDeliveryPoller.MAX_ATTEMPTS) }
        assertEquals(listOf(deliveryId), reclaimed.map { it.id })
    }

    @Test
    fun `repeated stranding spends the retry budget instead of redelivering forever`(wm: WireMockRuntimeInfo) {
        // The reclaim path used to be free: it set status and claimed_at but never touched
        // attempts, and attempts is bumped only by failOrReschedule — which by definition does not
        // run when the poller dies. So a row that stranded repeatedly was redelivered every lease
        // period forever, MAX_ATTEMPTS never applying, and it was never eligible for pruning
        // either because pruneOldDeliveries only sweeps terminal rows.
        //
        // The realistic trigger is not a dying machine: markDelivered swallows its own failures,
        // so a database error after a *successful* POST strands the row too. That is a duplicate
        // Discord message every five minutes, indefinitely.
        val worldId = createWorld("wh-strand-budget")
        val subId = insertSubscription(worldId, wm.httpBaseUrl + "/hook", "x", """["*"]""")
        val deliveryId = insertDelivery(subId)

        // The first claim comes off the PENDING arm — it is the delivery's first outing, not a
        // strand, so it deliberately does not spend an attempt.
        assertEquals(
            listOf(deliveryId),
            runBlocking { WebhookStore.claimDueDeliveries(WebhookDeliveryPoller.MAX_ATTEMPTS) }.map { it.id },
        )

        // Every reclaim after that does spend one.
        repeat(WebhookDeliveryPoller.MAX_ATTEMPTS) { strand ->
            ageClaim(deliveryId, minutes = 10)
            val reclaimed = runBlocking { WebhookStore.claimDueDeliveries(WebhookDeliveryPoller.MAX_ATTEMPTS) }
            assertEquals(listOf(deliveryId), reclaimed.map { it.id }, "strand #$strand should still be reclaimable")
            assertEquals(strand + 1, deliveryStatus(subId).second, "strand #$strand should have spent an attempt")
        }

        ageClaim(deliveryId, minutes = 10)
        assertTrue(
            runBlocking { WebhookStore.claimDueDeliveries(WebhookDeliveryPoller.MAX_ATTEMPTS) }.isEmpty(),
            "a row that has stranded MAX_ATTEMPTS times must stop being reclaimed",
        )

        // ...and must not be left IN_FLIGHT forever, which would leak the row and keep the poller
        // waking for a claim expiry that never resolves.
        runBlocking { WebhookStore.failAbandonedClaims(WebhookDeliveryPoller.MAX_ATTEMPTS) }
        assertEquals("FAILED", deliveryStatus(subId).first)
    }

    @Test
    fun `a poller whose lease lapsed cannot overwrite the outcome of the poller that superseded it`(
        wm: WireMockRuntimeInfo,
    ) {
        // The lease expiring lets someone else take the row, but on its own it does not fence the
        // original writer out. Poller A stalls past its lease; B reclaims, delivers and marks the
        // row DELIVERED; A's delayed failOrReschedule then lands and rewrites it back to PENDING
        // with an incremented attempt — and it is delivered a third time.
        //
        // Simulated by calling the store in that order, which is exactly what the two pollers do.
        val worldId = createWorld("wh-fencing")
        val subId = insertSubscription(worldId, wm.httpBaseUrl + "/hook", "x", """["*"]""")
        val deliveryId = insertDelivery(subId)

        runBlocking { WebhookStore.claimDueDeliveries(WebhookDeliveryPoller.MAX_ATTEMPTS) }   // poller A claims
        ageClaim(deliveryId, minutes = 10)                                                    // A stalls past its lease
        runBlocking { WebhookStore.claimDueDeliveries(WebhookDeliveryPoller.MAX_ATTEMPTS) }   // poller B reclaims
        runBlocking { WebhookStore.markDelivered(listOf(deliveryId)) }                        // B succeeds
        assertEquals("DELIVERED", deliveryStatus(subId).first)

        // A finally comes back and reports its failure.
        runBlocking { WebhookStore.failOrReschedule(listOf(deliveryId), WebhookDeliveryPoller.MAX_ATTEMPTS, "stale") }

        assertEquals(
            "DELIVERED", deliveryStatus(subId).first,
            "a superseded write must be a no-op, not a resurrection of a finished delivery",
        )
    }

    @Test
    fun `an expiring claim schedules a wake, so a stranded row is not left for the daily cleanup`(
        wm: WireMockRuntimeInfo,
    ) {
        val worldId = createWorld("wh-stranded-wake")
        val subId = insertSubscription(worldId, wm.httpBaseUrl + "/hook", "x", """["*"]""")
        insertDelivery(subId)

        runBlocking { WebhookStore.claimDueDeliveries(WebhookDeliveryPoller.MAX_ATTEMPTS) }

        // No PENDING row remains, so the old query returned null here and the poller would have
        // slept until the once-a-day cleanup — a 24 hour delay on a webhook.
        val next = runBlocking { WebhookStore.findNextScheduledDeliveryAt() }
        assertTrue(next != null, "an in-flight claim should still schedule a wake for its expiry")
    }

    @Test
    fun `a delivery carries its outbox row ids so a receiver can dedup our retries`(wm: WireMockRuntimeInfo) {
        val worldId = createWorld("wh-delivery-ids")
        val subId = insertSubscription(worldId, wm.httpBaseUrl + "/hook", "x", """["*"]""")
        val deliveryId = insertDelivery(subId)
        wm.wireMock.register(WireMock.post("/hook").willReturn(WireMock.aResponse().withStatus(200)))

        runBlocking { poller.pollOnce(System.currentTimeMillis()) }

        wm.wireMock.verifyThat(
            WireMock.postRequestedFor(WireMock.urlEqualTo("/hook"))
                .withHeader(WebhookDeliveryPoller.DELIVERY_IDS_HEADER, WireMock.equalTo("$deliveryId"))
        )
    }

    /** Backdates a claim so the lease looks expired, without sleeping through it. */
    private fun ageClaim(deliveryId: Long, minutes: Long) {
        Database.getConnection().use { conn ->
            conn.prepareStatement(
                "UPDATE webhook_deliveries SET claimed_at = CURRENT_TIMESTAMP - make_interval(mins => ?) WHERE id = ?"
            ).use { st ->
                st.setInt(1, minutes.toInt())
                st.setLong(2, deliveryId)
                st.executeUpdate()
            }
        }
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
        //
        // Each attempt claims the row first. That is not ceremony — failOrReschedule is fenced on
        // status = 'IN_FLIGHT', so a poller can only report an outcome for a row it actually
        // holds. Reporting one without claiming is exactly the stale write the fence exists to
        // suppress, and this test used to do it.
        val ids = listOf(deliveryIdFor(subId))
        repeat(2) {
            setNextAttemptAt(ids.single(), Instant.now().minusSeconds(1))
            runBlocking {
                WebhookStore.claimDueDeliveries(WebhookDeliveryPoller.MAX_ATTEMPTS)
                WebhookStore.failOrReschedule(ids, WebhookDeliveryPoller.MAX_ATTEMPTS, "boom")
            }
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
