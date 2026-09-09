package app.mcorg.api

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `GET /worlds/{id}/reporter` — "is anything reading my chests?" (MCO-536).
 *
 * The three states this separates are not cosmetic: from inside the game, *no server connected* and
 * *a server connected to a different Seam world* look identical — tags pile up and numbers never
 * move — and they have completely different fixes. The endpoint's whole job is to let the mod say
 * which one it is without the player opening a browser.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ReporterStatusIT : WithUser() {

    @Test
    fun `a world nobody has set up reports neither configured nor connected`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("no-reporter")

        val status = status(worldId)

        // This is the case that must be sayable in words: tagging works, and nothing will ever
        // read it, and the fix is to mint a token — not to tag harder.
        assertFalse(status.configured, "no token has been minted")
        assertFalse(status.connected)
        assertNull(status.lastSeenAt)
        assertEquals(0, status.serverCount)
    }

    @Test
    fun `a minted but unused token is configured and not connected`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("minted-unused")
        mintReporterToken(worldId, "the survival server")

        val status = status(worldId)

        // Distinct from the case above on purpose. Someone did set this up; the token just has not
        // reached a server yet, or the server has not run `/seam connect`. Same symptom in game,
        // different fix, so the same message would send the player to the wrong place.
        assertTrue(status.configured)
        assertFalse(status.connected, "no push has stamped it")
        assertNull(status.lastSeenAt)
        assertEquals(1, status.serverCount)
        assertEquals("the survival server", status.serverName)
    }

    @Test
    fun `a push makes the world connected, and says when and which build`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("connected")
        val token = mintReporterToken(worldId, "the survival server")
        heartbeat(token, worldId)

        val status = status(worldId)

        assertTrue(status.connected)
        assertNotNull(status.lastSeenAt, "a push must stamp the token")
        assertEquals("0.3.0+1.21.11", status.reporterVersion)
        assertEquals("the survival server", status.serverName)
    }

    @Test
    fun `an empty heartbeat counts as connected, which is the point of sending one`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("heartbeat-only")
        val token = mintReporterToken(worldId, "quiet server")

        // Nothing is tagged, so the reporter has nothing to say — and says so on cadence anyway.
        // If an empty push did not count, a healthy server watching an untagged world would report
        // as dead, which is exactly the false alarm this endpoint exists to prevent.
        heartbeat(token, worldId)

        assertTrue(status(worldId).connected)
    }

    @Test
    fun `the freshest server speaks for the world`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("two-servers")
        mintReporterToken(worldId, "stale server")
        val live = mintReporterToken(worldId, "live server")
        heartbeat(live, worldId)

        val status = status(worldId)

        // One live reporter is enough for the counts to move. Naming the never-connected one would
        // report a working world as broken.
        assertTrue(status.connected)
        assertEquals("live server", status.serverName)
        assertEquals(2, status.serverCount)
    }

    @Test
    fun `a non-member cannot ask about someone else's world`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("private")

        val response = client.get("/api/v1/worlds/$worldId/reporter") {
            header("Authorization", "Bearer ${issueToken(createExtraUser())}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status, response.bodyAsText())
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private suspend fun ApplicationTestBuilder.status(worldId: Int): ReporterStatusDto {
        val response = client.get("/api/v1/worlds/$worldId/reporter") {
            header("Authorization", "Bearer ${issueToken()}")
        }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return apiJson.decodeFromString(ReporterStatusDto.serializer(), response.bodyAsText())
    }

    /** An empty contents push — the reporter's heartbeat, which is what stamps `last_used_at`. */
    private suspend fun ApplicationTestBuilder.heartbeat(reporterToken: String, worldId: Int) {
        val response = client.post("/api/v1/reporter/contents") {
            header("Authorization", "Bearer $reporterToken")
            contentType(ContentType.Application.Json)
            setBody("""{"world_id":$worldId,"reporter_version":"0.3.0+1.21.11","containers":[]}""")
        }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
    }

    private fun issueToken(owner: app.mcorg.domain.model.user.TokenProfile = user): String = runBlocking {
        val token = ApiCrypto.newToken()
        CreateApiTokenStep.process(CreateApiTokenInput(owner.id, ApiCrypto.sha256Hex(token), "test", null))
        token
    }

    private fun mintReporterToken(worldId: Int, name: String): String = runBlocking {
        val token = ApiCrypto.newToken()
        CreateReporterTokenStep.process(
            CreateReporterTokenInput(worldId, ApiCrypto.sha256Hex(token), name, user.id)
        )
        token
    }

    private fun createWorld(name: String): Int = runBlocking {
        (CreateWorldStep(user).process(
            CreateWorldInput("$name-${System.nanoTime()}", "test", MinecraftVersion.fromString("1.21.4"))
        ) as Result.Success).value
    }
}
