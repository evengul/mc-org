package app.mcorg.api

import app.mcorg.config.Database
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.resources.commonsteps.UpsertProgressByItemInput
import app.mcorg.pipeline.resources.commonsteps.UpsertProgressByItemStep
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The reporter's surface and the measurement rollup (MCO-532) — the issue that makes the shared
 * count exist.
 *
 * The properties worth protecting here are the ones that would be silently wrong rather than
 * loudly broken: a partial push must not erase what it did not mention, an unreadable container
 * must not be counted as empty *or* as full, and a push must never touch the human's `collected`.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ReporterContentsIT : WithUser() {

    private val iron = "minecraft:iron_ingot"
    private val gold = "minecraft:gold_ingot"

    // ── The rollup ─────────────────────────────────────────────────────────────

    @Test
    fun `a push stores contents and rolls them up into the measurement`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("push-rollup")
        val projectId = createProject(worldId)
        val token = mintReporterToken(worldId)
        val a = tagContainer(worldId, projectId, 0, 64, 0)
        val b = tagContainer(worldId, projectId, 1, 64, 0)

        val response = push(
            token,
            container(a, iron to 12L),
            container(b, iron to 30L, gold to 4L),
        )
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())

        val storage = storage(worldId).associateBy { it.itemId }
        assertEquals(42, storage.getValue(iron).measured, "12 + 30 across two chests")
        assertEquals(2, storage.getValue(iron).containerCount)
        assertEquals(4, storage.getValue(gold).measured)
        assertEquals(1, storage.getValue(gold).containerCount)
        assertNotNull(storage.getValue(iron).oldestSeenAt)
    }

    @Test
    fun `a push naming a subset leaves the others' contributions intact`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("push-subset")
        val projectId = createProject(worldId)
        val token = mintReporterToken(worldId)
        val a = tagContainer(worldId, projectId, 0, 64, 0)
        val b = tagContainer(worldId, projectId, 1, 64, 0)
        push(token, container(a, iron to 12L), container(b, iron to 30L))

        // The reporter names only containers whose contents changed. Everything unnamed must keep
        // what it had — that is the entire reason the webapp stores per-container contents rather
        // than a total, and getting it wrong would make counts collapse on every sweep.
        push(token, container(a, iron to 20L))

        val storage = storage(worldId).single { it.itemId == iron }
        assertEquals(50, storage.measured, "20 from the re-read chest + 30 still standing")
        assertEquals(2, storage.containerCount)
    }

    @Test
    fun `an unreadable or missing container is excluded, and its state is still tellable`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("push-unreadable")
        val projectId = createProject(worldId)
        val token = mintReporterToken(worldId)
        val readable = tagContainer(worldId, projectId, 0, 64, 0)
        val gone = tagContainer(worldId, projectId, 1, 64, 0)
        push(token, container(readable, iron to 10L), container(gone, iron to 90L))
        assertEquals(100, storage(worldId).single { it.itemId == iron }.measured)

        // The chest was broken. Its 90 must stop counting — asserting stock that is not there is
        // worse than reporting less than you have.
        push(token, containerInState(gone, "missing"))

        val storage = storage(worldId).single { it.itemId == iron }
        assertEquals(10, storage.measured)
        assertEquals(1, storage.containerCount, "only the readable chest contributes")

        // …and the story of why is still tellable, from the tag's own state.
        val tags = containerTags(worldId).associateBy { it.id }
        assertEquals("missing", tags.getValue(gone).state)
        assertEquals("ok", tags.getValue(readable).state)
        assertNotNull(tags.getValue(gone).lastSeenAt, "the sweep saw it, and recorded when")
    }

    @Test
    fun `untagging drops the count immediately`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("push-untag")
        val projectId = createProject(worldId)
        val reporterToken = mintReporterToken(worldId)
        val playerToken = issueToken()
        val a = tagContainer(worldId, projectId, 0, 64, 0)
        val b = tagContainer(worldId, projectId, 1, 64, 0)
        push(reporterToken, container(a, iron to 12L), container(b, iron to 30L))

        val delete = deleteWithToken("/api/v1/worlds/$worldId/containers/$a", playerToken)
        assertEquals(HttpStatusCode.OK, delete.status)

        // The reporter never has to be told; the cascade removes the contents and the rollup is
        // re-run here rather than left claiming stock from a chest nobody is tracking any more.
        assertEquals(30, storage(worldId).single { it.itemId == iron }.measured)
    }

    // ── The line that must not be crossed ──────────────────────────────────────

    @Test
    fun `a push never touches collected`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("push-collected")
        val projectId = createProject(worldId)
        insertGatheringItem(projectId, iron, "Iron Ingot", required = 500)
        setCollected(projectId, iron, collected = 500, required = 500)
        val token = mintReporterToken(worldId)
        val a = tagContainer(worldId, projectId, 0, 64, 0)

        // The motivating case: you have 500 iron and typed 500 in. Then you tag your first chest
        // and the sweep finds 12. A design that let the measurement win would have just destroyed
        // a correct number with an incomplete measurement. `collected` is the human's; `measured`
        // is evidence; showing the disagreement is phase E's job, not this one's.
        push(token, container(a, iron to 12L))

        assertEquals(500, collectedFor(projectId, iron), "collected is the human's number")
        assertEquals(12, storage(worldId).single { it.itemId == iron }.measured)
    }

    // ── items_of_interest ──────────────────────────────────────────────────────

    @Test
    fun `items of interest covers target items and plan items`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("interest")
        val projectId = createProject(worldId)
        val token = mintReporterToken(worldId)
        insertGatheringItem(projectId, "minecraft:hopper", "Hopper", required = 64)
        // What the planner expands that into, as the materialised demand holds it.
        insertDemand(projectId, iron, "Iron Ingot")
        insertDemand(projectId, "#minecraft:planks", "Planks")

        val tags = reporterTags(token)
        val interest = tags.itemsOfInterest.single { it.projectId == projectId }.itemIds

        assertTrue("minecraft:hopper" in interest, "the target the build asks for")
        assertTrue(iron in interest, "the plan item you actually go and mine")
        // A tag is not a thing that can be sitting in a chest, so reporting it is pure payload.
        assertTrue(interest.none { it.startsWith("#") }, "tags are not reportable: $interest")
    }

    // ── Authorization ──────────────────────────────────────────────────────────

    @Test
    fun `a reporter token cannot report for another world`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("reporter-own")
        val otherWorldId = createWorld("reporter-other")
        val token = mintReporterToken(worldId)

        // The token fixes its world. Naming another is a misconfigured server, and saying so beats
        // silently substituting the right one.
        val response = client.post("/api/v1/reporter/contents") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"world_id":$otherWorldId,"containers":[]}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `a player token may report for a world it belongs to, and not for one it does not`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("sp-report")
        val projectId = createProject(worldId)
        val a = tagContainer(worldId, projectId, 0, 64, 0)
        val playerToken = issueToken()

        // Singleplayer has no server operator, so the integrated server sweeps as the player.
        val ok = client.post("/api/v1/reporter/contents") {
            header("Authorization", "Bearer $playerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"world_id":$worldId,"containers":[${containerJson(a, iron to 7L)}]}""")
        }
        assertEquals(HttpStatusCode.OK, ok.status, ok.bodyAsText())
        assertEquals(7, storage(worldId).single { it.itemId == iron }.measured)

        val strangerToken = issueToken(createExtraUser())
        val denied = client.post("/api/v1/reporter/contents") {
            header("Authorization", "Bearer $strangerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"world_id":$worldId,"containers":[]}""")
        }
        assertEquals(HttpStatusCode.Forbidden, denied.status)
    }

    @Test
    fun `a player token must name a world, because nothing else says which`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val response = client.post("/api/v1/reporter/contents") {
            header("Authorization", "Bearer ${issueToken()}")
            contentType(ContentType.Application.Json)
            setBody("""{"containers":[]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `a reporter token still opens no player route`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("reporter-still-scoped")
        val token = mintReporterToken(worldId)

        // MCO-531's property must survive this issue adding a route that *does* accept it.
        for (path in listOf("/api/v1/worlds", "/api/v1/worlds/$worldId/projects", "/api/v1/worlds/$worldId/storage")) {
            assertEquals(
                HttpStatusCode.Unauthorized,
                getWithToken(path, token).status,
                "a reporter token must not authenticate against $path",
            )
        }
    }

    @Test
    fun `storage is membership-gated`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("storage-gate")
        val strangerToken = issueToken(createExtraUser())
        assertEquals(
            HttpStatusCode.Forbidden,
            client.get("/api/v1/worlds/$worldId/storage") {
                header("Authorization", "Bearer $strangerToken")
            }.status,
        )
    }

    // ── The heartbeat ──────────────────────────────────────────────────────────

    @Test
    fun `an empty push is the heartbeat, and stamps the reporter`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("heartbeat")
        val token = mintReporterToken(worldId)

        // A reporter with nothing to report still pushes; that is why there is no separate
        // heartbeat endpoint.
        val response = client.post("/api/v1/reporter/contents") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"containers":[],"reporter_version":"0.3.0+1.21.11"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())

        val (lastUsed, version) = reporterStamp(worldId)
        assertNotNull(lastUsed, "an empty push is still a sign of life")
        assertEquals("0.3.0+1.21.11", version)
    }

    @Test
    fun `a malformed seen_at is rejected rather than stored`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("bad-seen-at")
        val projectId = createProject(worldId)
        val token = mintReporterToken(worldId)
        val a = tagContainer(worldId, projectId, 0, 64, 0)

        val response = client.post("/api/v1/reporter/contents") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"containers":[{"id":$a,"state":"ok","seen_at":"yesterday","items":[]}]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // --- helpers -------------------------------------------------------------

    private fun container(id: Long, vararg items: Pair<String, Long>) = containerJson(id, *items)

    private fun containerJson(id: Long, vararg items: Pair<String, Long>): String {
        val itemJson = items.joinToString(",") { """{"item_id":"${it.first}","count":${it.second}}""" }
        return """{"id":$id,"state":"ok","seen_at":"2026-09-08T10:00:00Z","items":[$itemJson]}"""
    }

    private fun containerInState(id: Long, state: String) =
        """{"id":$id,"state":"$state","seen_at":"2026-09-08T10:05:00Z","items":[]}"""

    private suspend fun ApplicationTestBuilder.push(token: String, vararg containers: String): HttpResponse =
        client.post("/api/v1/reporter/contents") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"swept_at":"2026-09-08T10:00:00Z","containers":[${containers.joinToString(",")}]}""")
        }

    private suspend fun ApplicationTestBuilder.storage(worldId: Int): List<WorldStorageDto> {
        val response = client.get("/api/v1/worlds/$worldId/storage") {
            header("Authorization", "Bearer ${issueToken()}")
        }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return apiJson.decodeFromString(ListSerializer(WorldStorageDto.serializer()), response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.containerTags(worldId: Int): List<ContainerTagDto> {
        val response = client.get("/api/v1/worlds/$worldId/containers") {
            header("Authorization", "Bearer ${issueToken()}")
        }
        return apiJson.decodeFromString(ContainerTagsResponse.serializer(), response.bodyAsText()).containers
    }

    private suspend fun ApplicationTestBuilder.reporterTags(token: String): ReporterTagsResponse {
        val response = client.get("/api/v1/reporter/tags") { header("Authorization", "Bearer $token") }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return apiJson.decodeFromString(ReporterTagsResponse.serializer(), response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.getWithToken(path: String, token: String): HttpResponse =
        client.get(path) { header("Authorization", "Bearer $token") }

    private suspend fun ApplicationTestBuilder.deleteWithToken(path: String, token: String): HttpResponse =
        client.delete(path) { header("Authorization", "Bearer $token") }

    private fun issueToken(user: TokenProfile = this.user): String = runBlocking {
        val token = ApiCrypto.newToken()
        CreateApiTokenStep.process(CreateApiTokenInput(user.id, ApiCrypto.sha256Hex(token), "test", null))
        token
    }

    private fun mintReporterToken(worldId: Int): String = runBlocking {
        val token = ApiCrypto.newToken()
        CreateReporterTokenStep.process(
            CreateReporterTokenInput(worldId, ApiCrypto.sha256Hex(token), "test reporter", user.id)
        )
        token
    }

    private fun createWorld(name: String): Int = runBlocking {
        (CreateWorldStep(user).process(
            CreateWorldInput("$name-${System.nanoTime()}", "test", MinecraftVersion.fromString("1.21.4"))
        ) as Result.Success).value
    }

    private fun createProject(worldId: Int): Int = runBlocking {
        (DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'BUILDING', 'RESOURCE_GATHERING', 'ACTIVE', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { st, _ ->
                st.setString(1, "Reporter IT Project ${System.nanoTime()}")
                st.setInt(2, worldId)
            }
        ).process(Unit) as Result.Success).value
    }

    private fun tagContainer(worldId: Int, projectId: Int, x: Int, y: Int, z: Int): Long =
        Database.getConnection().use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO container_tags
                    (world_id, project_id, dimension, x, y, z, group_key, kind, tagged_by)
                VALUES (?, ?, 'minecraft:overworld', ?, ?, ?, ?, 'chest', ?)
                RETURNING id
                """.trimIndent()
            ).use { st ->
                st.setInt(1, worldId); st.setInt(2, projectId)
                st.setInt(3, x); st.setInt(4, y); st.setInt(5, z)
                st.setString(6, "$x,$y,$z"); st.setInt(7, user.id)
                st.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
            }
        }

    private fun insertGatheringItem(projectId: Int, itemId: String, name: String, required: Int): Int =
        runBlocking {
            (DatabaseSteps.update<Unit>(
                sql = SafeSQL.insert(
                    "INSERT INTO resource_gathering (project_id, item_id, name, required) VALUES (?, ?, ?, ?) RETURNING id"
                ),
                parameterSetter = { st, _ ->
                    st.setInt(1, projectId); st.setString(2, itemId)
                    st.setString(3, name); st.setInt(4, required)
                }
            ).process(Unit) as Result.Success).value
        }

    /** Seeds the materialised plan demand directly — this test is about the union, not the planner. */
    private fun insertDemand(projectId: Int, itemId: String, name: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                """
                INSERT INTO project_demand (project_id, item_id, item_name, quantity, activity_group, node_status)
                VALUES (?, ?, ?, 1, 'GATHER', 'RAW_GATHER')
                """.trimIndent()
            ),
            parameterSetter = { st, _ ->
                st.setInt(1, projectId); st.setString(2, itemId); st.setString(3, name)
            }
        ).process(Unit)
    }

    private fun setCollected(projectId: Int, itemId: String, collected: Int, required: Long) = runBlocking {
        UpsertProgressByItemStep.process(
            UpsertProgressByItemInput(projectId, itemId, delta = collected, required = required)
        )
    }

    private fun collectedFor(projectId: Int, itemId: String): Int =
        Database.getConnection().use { conn ->
            conn.prepareStatement(
                "SELECT collected FROM resource_gathering_progress WHERE project_id = ? AND item_id = ?"
            ).use { st ->
                st.setInt(1, projectId); st.setString(2, itemId)
                st.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
        }

    private fun reporterStamp(worldId: Int): Pair<java.sql.Timestamp?, String?> =
        Database.getConnection().use { conn ->
            conn.prepareStatement(
                "SELECT last_used_at, reporter_version FROM reporter_token WHERE world_id = ? ORDER BY id DESC LIMIT 1"
            ).use { st ->
                st.setInt(1, worldId)
                st.executeQuery().use { rs ->
                    if (rs.next()) rs.getTimestamp(1) to rs.getString(2) else null to null
                }
            }
        }
}
