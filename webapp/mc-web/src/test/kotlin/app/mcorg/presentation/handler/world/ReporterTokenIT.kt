package app.mcorg.presentation.handler.world

import app.mcorg.api.ApiCrypto
import app.mcorg.api.apiV1Routes
import app.mcorg.config.CacheManager
import app.mcorg.config.Database
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.Role
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.pipeline.world.settings.handleMintReporterToken
import app.mcorg.pipeline.world.settings.handleRevokeReporterToken
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.WorldAdminPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.delete
import io.ktor.client.request.get
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
 * Reporter tokens (MCO-531) — minting, revoking, and the property the whole design rests on: a
 * reporter token is **not** a player token, and cannot be used as one.
 *
 * The reporter endpoints it *can* reach are MCO-532's, so what is provable here is the fail-closed
 * half. That is deliberately the half worth proving first: at this commit a reporter token opens
 * nothing at all, which is the safe intermediate state.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ReporterTokenIT : WithUser() {

    /** The raw token is rendered exactly once, inside the reveal block. */
    private val revealed = Regex("""<code class="reporter-reveal__token">([^<]+)</code>""")

    /**
     * The failure message deliberately omits the body: on the happy path it contains a live
     * credential, and a failing test writes its message to CI output.
     */
    private fun tokenFrom(body: String): String =
        revealed.find(body)?.groupValues?.get(1)
            ?: error("no token revealed (response was ${body.length} chars)")

    // ── Minting ────────────────────────────────────────────────────────────────

    @Test
    fun `minting reveals the token once and stores only its hash`() = testApplication {
        installRoutes()
        val worldId = createWorld("reporter-mint")

        val response = mint(worldId, name = "Mac Mini")
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val body = response.bodyAsText()
        val token = tokenFrom(body)
        assertTrue(body.contains("Mac Mini"))
        assertTrue(body.contains("Copy this token now"))

        val rows = tokensFor(worldId)
        val (hash, name) = rows.single()
        // What is stored is the hash of what was shown — a stolen row cannot be replayed.
        assertEquals(ApiCrypto.sha256Hex(token), hash)
        assertEquals("Mac Mini", name)
        assertFalse(hash == token, "the raw token must not be what is persisted")
    }

    @Test
    fun `the raw token never appears again after the mint that created it`() = testApplication {
        installRoutes()
        val worldId = createWorld("reporter-once")
        val first = tokenFrom(mint(worldId, name = "First").bodyAsText())

        // Any later render of the section — here, the response to minting a second token — must
        // carry the new token and no trace of the old one. Nothing can reproduce it: it was never
        // stored.
        val secondBody = mint(worldId, name = "Second").bodyAsText()
        assertFalse(secondBody.contains(first), "a previously minted token was rendered again")
        assertTrue(secondBody.contains("First"), "the earlier token's row should still be listed")
    }

    @Test
    fun `a token minted without a name is still usable and listed`() = testApplication {
        installRoutes()
        val worldId = createWorld("reporter-noname")

        val body = mint(worldId, name = null).bodyAsText()
        assertNotNull(tokenFrom(body))
        assertNull(tokensFor(worldId).single().second)
        assertTrue(body.contains("Unnamed server"))
    }

    @Test
    fun `a world with no reporter says so loudly`() = testApplication {
        installRoutes()
        val worldId = createWorld("reporter-none")
        val token = tokenFrom(mint(worldId, name = "Doomed").bodyAsText())
        assertNotNull(token)

        val afterRevoke = client.delete("/worlds/$worldId/settings/reporter/${idFor(worldId)}") {
            addAuthCookie(this, user)
        }
        val body = afterRevoke.bodyAsText()
        // This is the diagnosable failure the panel exists for: tags accumulate, nothing sweeps.
        assertTrue(body.contains("No server is connected to this world"), body)
        assertTrue(body.contains("stay at zero"), body)
    }

    @Test
    fun `the reveal opts out of htmx history, so the token never reaches sessionStorage`() = testApplication {
        installRoutes()
        val worldId = createWorld("reporter-history")

        val body = mint(worldId, name = "Cached?").bodyAsText()

        // Reproduced in a browser before this assertion existed: any HTMX request carrying
        // hx-push-url — the invitation tabs on this same page have it — makes htmx snapshot
        // document.body into sessionStorage['htmx-history-cache'] BEFORE swapping. That wrote the
        // plaintext token to storage readable by any same-origin script (and this app's CSP ships
        // 'unsafe-inline'), and pressing Back re-rendered it — contradicting the reveal's own
        // promise that it is shown once. hx-history="false" anywhere in the document suppresses
        // the snapshot, so it lives on the reveal and disappears with it.
        assertTrue(
            body.contains("""hx-history="false""""),
            "the token reveal must opt out of the htmx history cache",
        )
    }

    @Test
    fun `a freshly minted token reports that nothing has used it yet`() = testApplication {
        installRoutes()
        val worldId = createWorld("reporter-never")

        val body = mint(worldId, name = "Untouched").bodyAsText()
        assertTrue(body.contains("Never connected"), body)
        assertTrue(body.contains("No server has used this token yet"), body)
    }

    // ── Authorization ──────────────────────────────────────────────────────────

    @Test
    fun `a plain member cannot mint - 403 from WorldAdminPlugin`() = testApplication {
        installRoutes()
        val worldId = createWorld("reporter-auth")
        val member = createExtraUser()
        addWorldMember(member.id, worldId, Role.MEMBER, "member-${member.id}")

        val response = client.post("/worlds/$worldId/settings/reporter") {
            addAuthCookie(this, member)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("name" to "Sneaky").formUrlEncode())
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(0, tokensFor(worldId).size)
    }

    // ── Revocation ─────────────────────────────────────────────────────────────

    @Test
    fun `revoking takes the token out of the live list`() = testApplication {
        installRoutes()
        val worldId = createWorld("reporter-revoke")
        mint(worldId, name = "Retired")
        val id = idFor(worldId)

        val response = client.delete("/worlds/$worldId/settings/reporter/$id") {
            addAuthCookie(this, user)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(0, tokensFor(worldId).size)
        // The row survives revoked rather than being deleted, so the hash can never be re-minted.
        assertEquals(1, allRowsFor(worldId))
    }

    @Test
    fun `revoking is world-scoped - another world's token survives`() = testApplication {
        installRoutes()
        val worldA = createWorld("reporter-world-a")
        val worldB = createWorld("reporter-world-b")
        mint(worldB, name = "B's server")
        val idB = idFor(worldB)

        val response = client.delete("/worlds/$worldA/settings/reporter/$idB") {
            addAuthCookie(this, user)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, tokensFor(worldB).size, "world B's token must survive")
    }

    // ── The property the design rests on ───────────────────────────────────────

    @Test
    fun `a reporter token is not a player token and opens nothing`() = testApplication {
        installRoutes()
        val worldId = createWorld("reporter-not-a-player")
        val otherWorldId = createWorld("reporter-other-world")
        val token = tokenFrom(mint(worldId, name = "Reporter").bodyAsText())

        // ApiBearerAuthPlugin resolves hashes against api_token, and this hash is in reporter_token.
        // So the reporter is rejected by construction — no route had to remember to check.
        for (path in listOf(
            "/api/v1/worlds",
            "/api/v1/worlds/$worldId/projects",
            "/api/v1/worlds/$otherWorldId/projects",
        )) {
            val response = client.get(path) { header("Authorization", "Bearer $token") }
            assertEquals(
                HttpStatusCode.Unauthorized,
                response.status,
                "a reporter token must not authenticate against $path",
            )
        }
    }

    // --- helpers -------------------------------------------------------------

    private suspend fun ApplicationTestBuilder.mint(worldId: Int, name: String?) =
        client.post("/worlds/$worldId/settings/reporter") {
            addAuthCookie(this, user)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOfNotNull(name?.let { "name" to it }).formUrlEncode())
        }

    private fun ApplicationTestBuilder.installRoutes() {
        routing {
            install(AuthPlugin)
            apiV1Routes()
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                route("/settings") {
                    install(WorldAdminPlugin)
                    route("/reporter") {
                        post { call.handleMintReporterToken() }
                        delete("/{tokenId}") { call.handleRevokeReporterToken() }
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

    /** Live (unrevoked) tokens as (hash, name). */
    private fun tokensFor(worldId: Int): List<Pair<String, String?>> =
        Database.getConnection().use { conn ->
            conn.prepareStatement(
                "SELECT token_hash, name FROM reporter_token WHERE world_id = ? AND revoked_at IS NULL ORDER BY id"
            ).use { st ->
                st.setInt(1, worldId)
                st.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.getString(1) to rs.getString(2)) }
                }
            }
        }

    private fun allRowsFor(worldId: Int): Int =
        Database.getConnection().use { conn ->
            conn.prepareStatement("SELECT count(*) FROM reporter_token WHERE world_id = ?").use { st ->
                st.setInt(1, worldId)
                st.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
        }

    private fun idFor(worldId: Int): Long =
        Database.getConnection().use { conn ->
            conn.prepareStatement(
                "SELECT id FROM reporter_token WHERE world_id = ? AND revoked_at IS NULL ORDER BY id LIMIT 1"
            ).use { st ->
                st.setInt(1, worldId)
                st.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else error("no token for $worldId") }
            }
        }
}
