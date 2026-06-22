package app.mcorg.presentation.handler.world

import app.mcorg.config.AppConfig
import app.mcorg.config.CacheManager
import app.mcorg.config.Database
import app.mcorg.domain.model.minecraft.MinecraftVersion
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
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import app.mcorg.webhook.CreateWebhookSubscriptionInput
import app.mcorg.webhook.CreateWebhookSubscriptionStep
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

    private fun configure() {
        AppConfig.seamDiscordUrl = discordBase
        AppConfig.webhookSharedSecret = sharedSecret
    }

    @Test
    fun `connect creates a world-scoped subscription with discord callback and metadata`() = testApplication {
        configure()
        installRoutes()
        val worldId = createWorld("discord-connect")

        val response = client.post("/worlds/$worldId/settings/discord") {
            addAuthCookie(this, user)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("channel_id" to channelId, "compact" to "true").formUrlEncode())
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Channel $channelId"))

        val rows = subscriptionsFor(worldId)
        assertEquals(1, rows.size)
        val (callbackUrl, secret, metadata) = rows.single()
        assertEquals("$discordBase/seam-events/$channelId?compact=1", callbackUrl)
        assertEquals(sharedSecret, secret)
        assertTrue(metadata.contains(""""discord_channel_id":"$channelId"""") && metadata.contains(""""compact":true"""))
    }

    @Test
    fun `connect without compact omits the query flag`() = testApplication {
        configure()
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
    fun `invalid channel id is rejected and creates no subscription`() = testApplication {
        configure()
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
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("channel_id" to channelId).formUrlEncode())
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals(0, subscriptionsFor(worldId).size)
    }

    @Test
    fun `non-admin member cannot connect - 403 from WorldAdminPlugin`() = testApplication {
        configure()
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
        configure()
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
        configure()
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

    private fun createSubscription(worldId: Int, callbackUrl: String): Int = runBlocking {
        (CreateWebhookSubscriptionStep.process(
            CreateWebhookSubscriptionInput(worldId, callbackUrl, sharedSecret, """["*"]""", "{}")
        ) as Result.Success).value
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
