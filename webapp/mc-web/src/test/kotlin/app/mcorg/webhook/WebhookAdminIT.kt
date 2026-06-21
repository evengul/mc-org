package app.mcorg.webhook

import app.mcorg.config.AppConfig
import app.mcorg.config.Database
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.WEBHOOK_ADMIN_SECRET_HEADER
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
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
class WebhookAdminIT : WithUser() {

    private val secret = "admin-shared-secret"

    @AfterEach
    fun resetConfig() {
        AppConfig.webhookAdminSecret = null
    }

    @Test
    fun `creates a subscription with a valid secret and form params`() = testApplication {
        AppConfig.webhookAdminSecret = secret
        configureRoutes()
        val worldId = createWorld("wh-admin-create")

        val response = create(
            "world_id" to worldId.toString(),
            "callback_url" to "https://example.com/hook",
            "secret" to "subscriber-secret",
            "event_filter" to """["project_created"]""",
            secret = secret,
        )

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("Created webhook subscription #"))
        assertEquals(1, countSubscriptions(worldId))
    }

    @Test
    fun `defaults the event filter to wildcard when omitted`() = testApplication {
        AppConfig.webhookAdminSecret = secret
        configureRoutes()
        val worldId = createWorld("wh-admin-default-filter")

        create(
            "world_id" to worldId.toString(),
            "callback_url" to "https://example.com/hook",
            "secret" to "subscriber-secret",
            secret = secret,
        )

        assertEquals("""["*"]""", eventFilterFor(worldId))
    }

    @Test
    fun `rejects a missing or wrong secret`() = testApplication {
        AppConfig.webhookAdminSecret = secret
        configureRoutes()
        val worldId = createWorld("wh-admin-auth")

        val noHeader = create("world_id" to worldId.toString(), "callback_url" to "https://e.com/h", "secret" to "subscriber-secret", secret = null)
        assertEquals(HttpStatusCode.Unauthorized, noHeader.status)

        val wrongHeader = create("world_id" to worldId.toString(), "callback_url" to "https://e.com/h", "secret" to "subscriber-secret", secret = "nope")
        assertEquals(HttpStatusCode.Unauthorized, wrongHeader.status)

        assertEquals(0, countSubscriptions(worldId))
    }

    @Test
    fun `fails closed with 503 when no admin secret is configured`() = testApplication {
        AppConfig.webhookAdminSecret = null
        configureRoutes()
        val worldId = createWorld("wh-admin-unconfigured")

        val response = create("world_id" to worldId.toString(), "callback_url" to "https://e.com/h", "secret" to "subscriber-secret", secret = "anything")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }

    @Test
    fun `rejects invalid input`() = testApplication {
        AppConfig.webhookAdminSecret = secret
        configureRoutes()
        createWorld("wh-admin-invalid")

        val missingWorld = create("callback_url" to "https://e.com/h", "secret" to "subscriber-secret", secret = secret)
        assertEquals(HttpStatusCode.BadRequest, missingWorld.status)

        val badUrl = create("world_id" to "1", "callback_url" to "not-a-url", "secret" to "subscriber-secret", secret = secret)
        assertEquals(HttpStatusCode.UnprocessableEntity, badUrl.status)
    }

    @Test
    fun `deletes a subscription`() = testApplication {
        AppConfig.webhookAdminSecret = secret
        configureRoutes()
        val worldId = createWorld("wh-admin-delete")
        val id = runBlocking {
            (CreateWebhookSubscriptionStep.process(
                CreateWebhookSubscriptionInput(worldId, "https://e.com/h", "subscriber-secret", """["*"]""", "{}")
            ) as Result.Success).value
        }

        val response = delete(id, secret)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(0, countSubscriptions(worldId))

        val again = delete(id, secret)
        assertEquals(HttpStatusCode.NotFound, again.status)
    }

    // --- helpers -------------------------------------------------------------

    private fun ApplicationTestBuilder.configureRoutes() {
        application {
            routing {
                install(AuthPlugin)
                webhookAdminRoutes()
            }
        }
    }

    private suspend fun ApplicationTestBuilder.create(vararg params: Pair<String, String>, secret: String?): HttpResponse =
        client.post("/integrations/webhooks") {
            if (secret != null) header(WEBHOOK_ADMIN_SECRET_HEADER, secret)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(params.toList().formUrlEncode())
        }

    private suspend fun ApplicationTestBuilder.delete(id: Int, secret: String): HttpResponse =
        client.request("/integrations/webhooks/$id") {
            method = HttpMethod.Delete
            header(WEBHOOK_ADMIN_SECRET_HEADER, secret)
        }

    private fun createWorld(name: String): Int = runBlocking {
        (CreateWorldStep(user).process(CreateWorldInput(name, "test", MinecraftVersion.fromString("1.21.4"))) as Result.Success).value
    }

    private fun countSubscriptions(worldId: Int): Int =
        Database.getConnection().use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM webhook_subscriptions WHERE world_id = ?").use { st ->
                st.setInt(1, worldId)
                st.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
            }
        }

    private fun eventFilterFor(worldId: Int): String =
        Database.getConnection().use { conn ->
            conn.prepareStatement("SELECT event_filter::text FROM webhook_subscriptions WHERE world_id = ?").use { st ->
                st.setInt(1, worldId)
                st.executeQuery().use { rs -> rs.next(); rs.getString(1) }
            }
        }
}
