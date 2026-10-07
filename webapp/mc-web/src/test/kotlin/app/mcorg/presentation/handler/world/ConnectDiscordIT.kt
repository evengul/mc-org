package app.mcorg.presentation.handler.world

import app.mcorg.config.AppConfig
import app.mcorg.config.CacheManager
import app.mcorg.config.Database
import app.mcorg.event.ProjectCreated
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.user.Role
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.pipeline.world.settings.handleConnectDiscord
import app.mcorg.pipeline.world.settings.handleDisconnectDiscord
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.WorldAdminPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import app.mcorg.webhook.CreateWebhookSubscriptionInput
import app.mcorg.webhook.CreateWebhookSubscriptionStep
import app.mcorg.webhook.WebhookFanoutConsumer
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.server.application.install
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ConnectDiscordIT : WithUser() {

    private val discordBase = "https://disc.example.com"
    private val sharedSecret = "shared-bot-secret"
    private val channelId = "123456789012345678"

    @AfterEach
    fun resetConfig() {
        AppConfig.seamDiscordUrl = null
        AppConfig.webhookSharedSecret = null
    }

    // Not `configure()`: inside `testApplication { }` that name resolves to
    // ApplicationTestBuilder.configure, whose parameters all have defaults, so the call compiles,
    // does nothing to AppConfig, and every connect test sees the fail-closed 503 (MCO-301).
    private fun configureDiscord() {
        AppConfig.seamDiscordUrl = discordBase
        AppConfig.webhookSharedSecret = sharedSecret
    }

    @Test
    fun `connect creates a world-scoped subscription with discord callback and metadata`() = testApplication {
        configureDiscord()
        installRoutes()
        val worldId = createWorld("discord-connect")

        val response = client.post("/worlds/$worldId/settings/discord") {
            addAuthCookie(this, user)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("channel_id" to channelId, "compact" to "true").formUrlEncode())
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertTrue(response.bodyAsText().contains("Channel $channelId"))

        val rows = subscriptionsFor(worldId)
        assertEquals(1, rows.size)
        val (callbackUrl, secret, metadata) = rows.single()
        assertEquals("$discordBase/seam-events/$channelId?compact=1", callbackUrl)
        assertEquals(sharedSecret, secret)
        // jsonb::text normalises spacing (`"compact": true`), so compare parsed values, not substrings.
        val fields = Json.parseToJsonElement(metadata).jsonObject
        assertEquals(channelId, fields["discord_channel_id"]?.jsonPrimitive?.content)
        assertEquals(true, fields["compact"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun `connect without compact omits the query flag`() = testApplication {
        configureDiscord()
        installRoutes()
        val worldId = createWorld("discord-connect-plain")

        client.post("/worlds/$worldId/settings/discord") {
            addAuthCookie(this, user)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("channel_id" to channelId).formUrlEncode())
        }

        assertEquals("$discordBase/seam-events/$channelId", subscriptionsFor(worldId).single().first)
    }

    @Test
    fun `reconnecting a channel updates its subscription instead of adding a second one`() = testApplication {
        configureDiscord()
        installRoutes()
        val worldId = createWorld("discord-reconnect")

        connect(worldId, channelId, compact = true)
        connect(worldId, channelId, compact = false)

        val rows = subscriptionsFor(worldId)
        assertEquals(1, rows.size, "Connecting the same channel twice must not leave two subscriptions")
        assertEquals("$discordBase/seam-events/$channelId", rows.single().first)
        val fields = Json.parseToJsonElement(rows.single().third).jsonObject
        assertEquals(false, fields["compact"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun `reconnecting over a wildcard subscription narrows it and posts each event once`() = testApplication {
        configureDiscord()
        installRoutes()
        val worldId = createWorld("discord-reconnect-wildcard")
        // A subscription from before MCO-358 narrowed the filter: ["*"], same channel.
        createSubscription(worldId, "$discordBase/seam-events/$channelId", channelId = channelId)

        connect(worldId, channelId, compact = false)

        assertEquals(1, activeSubscriptionCount(worldId))
        val filter = Json.parseToJsonElement(eventFiltersFor(worldId).single()).jsonArray.map { it.jsonPrimitive.content }
        assertEquals(
            listOf(
                "project_created",
                "project_status_changed",
                "project_resources_complete",
                "project_unblocked",
                "resource_milestone_reached",
            ),
            filter,
            "Reconnecting must replace the wildcard with the types seam-discord renders",
        )

        WebhookFanoutConsumer().handle(
            ProjectCreated(worldId, user.id, Instant.now(), 1, "Iron Farm", ProjectType.REDSTONE)
        )
        assertEquals(1, deliveryCountFor(worldId), "One channel, one event: one delivery")
    }

    @Test
    fun `a deactivated subscription does not block reconnecting the channel`() = testApplication {
        configureDiscord()
        installRoutes()
        val worldId = createWorld("discord-reconnect-inactive")
        val old = createSubscription(worldId, "$discordBase/seam-events/$channelId", channelId = channelId)
        deactivate(old)

        connect(worldId, channelId, compact = false)

        assertEquals(2, subscriptionsFor(worldId).size)
        assertEquals(1, activeSubscriptionCount(worldId))
    }

    @Test
    fun `a second channel in the same world gets its own subscription`() = testApplication {
        configureDiscord()
        installRoutes()
        val worldId = createWorld("discord-two-channels")

        connect(worldId, channelId, compact = false)
        connect(worldId, "876543210987654321", compact = false)

        assertEquals(2, activeSubscriptionCount(worldId))
    }

    @Test
    fun `invalid channel id is rejected and creates no subscription`() = testApplication {
        configureDiscord()
        installRoutes()
        val worldId = createWorld("discord-invalid")

        val response = client.post("/worlds/$worldId/settings/discord") {
            addAuthCookie(this, user)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("channel_id" to "not-a-snowflake").formUrlEncode())
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(0, subscriptionsFor(worldId).size)
    }

    @Test
    fun `not configured fails closed and creates no subscription`() = testApplication {
        // config intentionally not set
        installRoutes()
        val worldId = createWorld("discord-unconfigured")

        val response = client.post("/worlds/$worldId/settings/discord") {
            addAuthCookie(this, user)
            header("HX-Request", "true")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("channel_id" to channelId).formUrlEncode())
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("isn't configured"), response.bodyAsText())
        assertTrue(response.bodyAsText().contains("hx-target=\"#discord-section\""), "re-rendered in place: ${response.bodyAsText()}")
        assertEquals(0, subscriptionsFor(worldId).size)
    }

    @Test
    fun `non-admin member cannot connect - 403 from WorldAdminPlugin`() = testApplication {
        configureDiscord()
        installRoutes()
        val worldId = createWorld("discord-auth")
        val member = createExtraUser()
        addWorldMember(member.id, worldId, Role.MEMBER, "member-${member.id}")

        val response = client.post("/worlds/$worldId/settings/discord") {
            addAuthCookie(this, member)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("channel_id" to channelId).formUrlEncode())
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(0, subscriptionsFor(worldId).size)
    }

    @Test
    fun `disconnect removes the subscription`() = testApplication {
        configureDiscord()
        installRoutes()
        val worldId = createWorld("discord-disconnect")
        val id = createSubscription(worldId, "$discordBase/seam-events/$channelId")

        val response = client.delete("/worlds/$worldId/settings/discord/$id") {
            addAuthCookie(this, user)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(0, subscriptionsFor(worldId).size)
    }

    @Test
    fun `disconnect is world-scoped - cannot delete another world's subscription`() = testApplication {
        configureDiscord()
        installRoutes()
        val worldA = createWorld("discord-world-a")
        val worldB = createWorld("discord-world-b")
        val idB = createSubscription(worldB, "$discordBase/seam-events/$channelId")

        // Admin of world A tries to delete world B's subscription by id, scoped to world A's path.
        val response = client.delete("/worlds/$worldA/settings/discord/$idB") {
            addAuthCookie(this, user)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        // World B's subscription must survive — the world-scoped DELETE matched nothing.
        assertEquals(1, subscriptionsFor(worldB).size)
    }

    // --- helpers -------------------------------------------------------------

    private fun ApplicationTestBuilder.installRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                route("/settings") {
                    install(WorldAdminPlugin)
                    route("/discord") {
                        post { call.handleConnectDiscord() }
                        delete("/{subscriptionId}") { call.handleDisconnectDiscord() }
                    }
                }
            }
        }
    }

    private fun createWorld(name: String): Int = runBlocking {
        (CreateWorldStep(user).process(
            CreateWorldInput("$name-${System.nanoTime()}", "test", MinecraftVersion.fromString("1.21.4"))
        ) as Result.Success).value
    }

    private fun createSubscription(worldId: Int, callbackUrl: String, channelId: String? = null): Int = runBlocking {
        val metadata = channelId?.let { """{"discord_channel_id":"$it","compact":false}""" } ?: "{}"
        (CreateWebhookSubscriptionStep.process(
            CreateWebhookSubscriptionInput(worldId, callbackUrl, sharedSecret, """["*"]""", metadata)
        ) as Result.Success).value
    }

    /** Connects through the route and fails the test on anything but 200, so a broken connect reads as one. */
    private suspend fun ApplicationTestBuilder.connect(worldId: Int, channelId: String, compact: Boolean) {
        val response = client.post("/worlds/$worldId/settings/discord") {
            addAuthCookie(this, user)
            contentType(ContentType.Application.FormUrlEncoded)
            val form = listOf("channel_id" to channelId) + if (compact) listOf("compact" to "true") else emptyList()
            setBody(form.formUrlEncode())
        }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
    }

    private fun deactivate(subscriptionId: Int) {
        Database.getConnection().use { conn ->
            conn.prepareStatement("UPDATE webhook_subscriptions SET active = false WHERE id = ?").use { st ->
                st.setInt(1, subscriptionId)
                st.executeUpdate()
            }
        }
    }

    private fun activeSubscriptionCount(worldId: Int): Int =
        countFor("SELECT count(*) FROM webhook_subscriptions WHERE world_id = ? AND active = true", worldId)

    private fun deliveryCountFor(worldId: Int): Int = countFor(
        """
        SELECT count(*) FROM webhook_deliveries d
        JOIN webhook_subscriptions s ON s.id = d.subscription_id
        WHERE s.world_id = ?
        """.trimIndent(),
        worldId,
    )

    private fun countFor(sql: String, worldId: Int): Int =
        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { st ->
                st.setInt(1, worldId)
                st.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
            }
        }

    private fun eventFiltersFor(worldId: Int): List<String> =
        Database.getConnection().use { conn ->
            conn.prepareStatement(
                "SELECT event_filter::text FROM webhook_subscriptions WHERE world_id = ? AND active = true"
            ).use { st ->
                st.setInt(1, worldId)
                st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
            }
        }

    private fun addWorldMember(userId: Int, worldId: Int, role: Role, displayName: String) {
        runBlocking {
            DatabaseSteps.update<Unit>(
                SafeSQL.insert("INSERT INTO world_members (user_id, world_id, display_name, world_role) VALUES (?, ?, ?, ?)"),
                parameterSetter = { stmt, _ ->
                    stmt.setInt(1, userId)
                    stmt.setInt(2, worldId)
                    stmt.setString(3, displayName)
                    stmt.setInt(4, role.level)
                }
            ).process(Unit)
            CacheManager.onMemberAdded(userId, worldId)
            CacheManager.worldMemberRole.asMap().keys
                .filter { it.startsWith("$userId:$worldId:") }
                .forEach { CacheManager.worldMemberRole.invalidate(it) }
        }
    }

    private fun subscriptionsFor(worldId: Int): List<Triple<String, String, String>> =
        Database.getConnection().use { conn ->
            conn.prepareStatement(
                "SELECT callback_url, secret, metadata::text FROM webhook_subscriptions WHERE world_id = ?"
            ).use { st ->
                st.setInt(1, worldId)
                st.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(Triple(rs.getString(1), rs.getString(2), rs.getString(3)))
                    }
                }
            }
        }
}
