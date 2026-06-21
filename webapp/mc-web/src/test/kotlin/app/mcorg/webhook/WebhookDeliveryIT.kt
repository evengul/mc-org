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
}
