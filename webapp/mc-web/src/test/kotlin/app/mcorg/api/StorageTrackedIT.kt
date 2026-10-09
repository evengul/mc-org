package app.mcorg.api

import app.mcorg.config.Database
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.resources.GetStorageTrackingStep
import app.mcorg.pipeline.resources.SetStorageTrackedInput
import app.mcorg.pipeline.resources.SetStorageTrackedStep
import app.mcorg.pipeline.resources.commonsteps.UpsertProgressByItemInput
import app.mcorg.pipeline.resources.commonsteps.UpsertProgressByItemStep
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.handler.WorldHandler
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Storage-tracked: a project whose counts follow its chests (MCO-540).
 *
 * [AdoptMeasurementIT] pins the default — a sweep never moves a typed number. This pins the
 * opt-in: once someone switches a project over, every sweep moves every count, an item in no chest
 * reads 0, and nothing typed by hand is accepted until it is switched back.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class StorageTrackedIT : WithUser() {

    private val iron = "minecraft:iron_ingot"
    private val gold = "minecraft:gold_ingot"

    // ── Following ────────────────────────────────────────────────────────────

    @Test
    fun `a sweep moves a tracked project's count, stamped as the mod's`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val fixture = trackedProject("follows")
        setCollected(fixture.projectId, iron, 500)
        trackOn(fixture.projectId)

        push(fixture.reporterToken, fixture.chest, iron to 40L)

        assertEquals(40, collectedOf(fixture.projectId, iron))
        assertEquals("mod", progressSourceOf(fixture.projectId, iron))
    }

    @Test
    fun `an item in none of the chests reads 0 on a tracked project`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val fixture = trackedProject("zeroes")
        insertGathering(fixture.projectId, gold, required = 512)
        setCollected(fixture.projectId, gold, 300)
        trackOn(fixture.projectId)

        // The chest holds iron and no gold. Leaving gold at 300 would let a number nobody can see
        // in any chest keep claiming progress.
        push(fixture.reporterToken, fixture.chest, iron to 12L)

        assertEquals(12, collectedOf(fixture.projectId, iron))
        assertEquals(0, collectedOf(fixture.projectId, gold))
    }

    @Test
    fun `a plan item counted by hand and in no chest reads 0 too`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val fixture = trackedProject("plan-item")
        // A plan item has a progress row and no target row — the work list counts it by item id.
        // It is in neither the targets nor the measurement, so only its own progress row names it.
        val stick = "minecraft:stick"
        setCollected(fixture.projectId, stick, 30)
        trackOn(fixture.projectId)

        push(fixture.reporterToken, fixture.chest, iron to 12L)

        assertEquals(0, collectedOf(fixture.projectId, stick))
    }

    @Test
    fun `a target never counted and in no chest gets no row written`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val fixture = trackedProject("no-zero-rows")
        insertGathering(fixture.projectId, gold, required = 512)
        trackOn(fixture.projectId)

        // A schematic import carries hundreds of targets. Writing a zero for each one would be
        // hundreds of rows that say what no row already says.
        push(fixture.reporterToken, fixture.chest, iron to 12L)

        assertFalse(progressRowExists(fixture.projectId, gold), "a zero row was written for gold")
        assertEquals(12, collectedOf(fixture.projectId, iron))
    }

    @Test
    fun `an emptied chest takes a followed count back to 0`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val fixture = trackedProject("emptied")
        trackOn(fixture.projectId)
        push(fixture.reporterToken, fixture.chest, iron to 64L)
        assertEquals(64, collectedOf(fixture.projectId, iron))

        push(fixture.reporterToken, fixture.chest)

        assertEquals(0, collectedOf(fixture.projectId, iron))
    }

    @Test
    fun `a followed count is capped at required`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val fixture = trackedProject("capped", required = 64)
        trackOn(fixture.projectId)

        push(fixture.reporterToken, fixture.chest, iron to 640L)

        assertEquals(64, collectedOf(fixture.projectId, iron))
    }

    @Test
    fun `an item required by two rows is capped at their sum, and written once`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val fixture = trackedProject("two-rows", required = 64)
        insertGathering(fixture.projectId, iron, required = 64)
        trackOn(fixture.projectId)

        // Two target rows for one item would make the upsert write one progress row twice, which
        // Postgres refuses outright — so this also proves the follow does not fail the sweep.
        push(fixture.reporterToken, fixture.chest, iron to 100L)

        assertEquals(100, collectedOf(fixture.projectId, iron))
    }

    // ── Switching ────────────────────────────────────────────────────────────

    @Test
    fun `switching on follows the chests at once, without waiting for a sweep`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val fixture = trackedProject("immediate")
        setCollected(fixture.projectId, iron, 500)
        push(fixture.reporterToken, fixture.chest, iron to 12L)
        assertEquals(500, collectedOf(fixture.projectId, iron), "untracked: the sweep moved a typed count")

        trackOn(fixture.projectId)

        assertEquals(12, collectedOf(fixture.projectId, iron))
    }

    @Test
    fun `a project with no tagged containers cannot be switched on`() {
        val worldId = createWorld("no-tags")
        val projectId = createProject(worldId)
        insertGathering(projectId, iron, required = 512)
        setCollected(projectId, iron, 500)

        val result = runBlocking { SetStorageTrackedStep.process(SetStorageTrackedInput(projectId, true)) }

        val failure = assertIs<Result.Failure<AppFailure>>(result)
        assertIs<AppFailure.ValidationError>(failure.error)
        assertFalse(trackingOf(projectId).tracked)
        assertEquals(500, collectedOf(projectId, iron), "a refused switch zeroed a typed count")
    }

    @Test
    fun `a project whose only tags have never been read cannot be switched on`() {
        val worldId = createWorld("unread-tags")
        val projectId = createProject(worldId)
        insertGathering(projectId, iron, required = 512)
        setCollected(projectId, iron, 500)
        // Tagged, but no sweep has read it: nothing feeds the measurement, so following it would
        // zero every count exactly as following no tags at all would.
        tagContainer(worldId, projectId, state = "unreadable")

        val result = runBlocking { SetStorageTrackedStep.process(SetStorageTrackedInput(projectId, true)) }

        assertIs<Result.Failure<AppFailure>>(result)
        assertFalse(trackingOf(projectId).tracked)
        assertEquals(500, collectedOf(projectId, iron), "a refused switch zeroed a typed count")
    }

    @Test
    fun `changing a target's required re-follows a tracked count`() = testApplication {
        routing {
            install(AuthPlugin)
            apiV1Routes()
            with(WorldHandler()) { worldRoutes() }
        }
        val fixture = trackedProject("required-change", required = 64)
        trackOn(fixture.projectId)
        push(fixture.reporterToken, fixture.chest, iron to 100L)
        assertEquals(64, collectedOf(fixture.projectId, iron))

        // No sweep follows this: the chest has not changed, so nothing would push.
        val response = client.patch(
            "/worlds/${fixture.worldId}/projects/${fixture.projectId}/resources/gathering/${fixture.gatheringId}/required"
        ) {
            addAuthCookie(this)
            header("HX-Request", "true")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("required=128")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(100, collectedOf(fixture.projectId, iron))
    }

    @Test
    fun `switching off keeps the followed counts, and the next sweep leaves them alone`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val fixture = trackedProject("off-again")
        trackOn(fixture.projectId)
        push(fixture.reporterToken, fixture.chest, iron to 40L)

        runBlocking { SetStorageTrackedStep.process(SetStorageTrackedInput(fixture.projectId, false)) }
        push(fixture.reporterToken, fixture.chest, iron to 90L)

        assertFalse(trackingOf(fixture.projectId).tracked)
        assertEquals(40, collectedOf(fixture.projectId, iron))
    }

    @Test
    fun `removing the last tag switches tracking off instead of zeroing the project`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val fixture = trackedProject("untagged")
        trackOn(fixture.projectId)
        push(fixture.reporterToken, fixture.chest, iron to 40L)

        deleteTag(fixture.chest)
        runBlocking { RecomputeMeasurementStep.process(fixture.projectId) }

        assertFalse(trackingOf(fixture.projectId).tracked)
        assertEquals(40, collectedOf(fixture.projectId, iron))
    }

    // ── Refusing typed counts ────────────────────────────────────────────────

    @Test
    fun `the field log counter refuses a typed count on a tracked project`() = testApplication {
        installWorldRoutes()
        val fixture = trackedProject("edit-done")
        trackOn(fixture.projectId)

        val response = client.patch(
            "/worlds/${fixture.worldId}/projects/${fixture.projectId}/resources/gathering/${fixture.gatheringId}/edit-done"
        ) {
            addAuthCookie(this)
            header("HX-Request", "true")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("amount=64")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertContains(response.bodyAsText(), "follow its tagged chests")
        assertEquals(0, collectedOf(fixture.projectId, iron))
    }

    @Test
    fun `setting a count refuses on a tracked project`() = testApplication {
        installWorldRoutes()
        val fixture = trackedProject("set-value")
        trackOn(fixture.projectId)

        val response = client.put(
            "/worlds/${fixture.worldId}/projects/${fixture.projectId}/resources/gathering/${fixture.gatheringId}/collected"
        ) {
            addAuthCookie(this)
            header("HX-Request", "true")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("value=64")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(0, collectedOf(fixture.projectId, iron))
    }

    @Test
    fun `the plan's counter refuses on a tracked project`() = testApplication {
        installWorldRoutes()
        val fixture = trackedProject("plan-progress")
        trackOn(fixture.projectId)

        val response = client.patch("/worlds/${fixture.worldId}/projects/${fixture.projectId}/plan/progress") {
            addAuthCookie(this)
            header("HX-Request", "true")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=$iron&amount=64&required=512")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(0, collectedOf(fixture.projectId, iron))
    }

    @Test
    fun `the field log counter still writes on an untracked project`() = testApplication {
        installWorldRoutes()
        val fixture = trackedProject("untracked-writes")

        val response = client.patch(
            "/worlds/${fixture.worldId}/projects/${fixture.projectId}/resources/gathering/${fixture.gatheringId}/edit-done"
        ) {
            addAuthCookie(this)
            header("HX-Request", "true")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("amount=64")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(64, collectedOf(fixture.projectId, iron))
    }

    @Test
    fun `the mod's sync is refused with 409 on a tracked project`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val fixture = trackedProject("api-sync")
        trackOn(fixture.projectId)
        val token = issueApiToken()

        val response = client.post("/api/v1/projects/${fixture.projectId}/resources/sync") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"resources":[{"item_id":"$iron","collected":100}]}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertContains(response.bodyAsText(), "storage_tracked")
        assertEquals(0, collectedOf(fixture.projectId, iron))
    }

    // ── The settings page ────────────────────────────────────────────────────

    @Test
    fun `a member opens project settings, and sees no danger zone`() = testApplication {
        installWorldRoutes()
        val fixture = trackedProject("settings-member")
        val member = createExtraUser()
        addWorldMember(fixture.worldId, member, Role.MEMBER)

        val response = client.get("/worlds/${fixture.worldId}/projects/${fixture.projectId}/settings") {
            addAuthCookie(this, member)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Chest counts")
        assertContains(body, "Count from chests")
        // Delete is admin-only at the route; the page does not offer what the route will refuse.
        assertFalse(body.contains("Delete project"), "a member was offered the delete")
    }

    @Test
    fun `the owner sees the danger zone on project settings`() = testApplication {
        installWorldRoutes()
        val fixture = trackedProject("settings-owner")

        val body = client.get("/worlds/${fixture.worldId}/projects/${fixture.projectId}/settings") {
            addAuthCookie(this)
        }.bodyAsText()

        assertContains(body, "Delete project")
    }

    @Test
    fun `someone outside the world cannot open project settings`() = testApplication {
        installWorldRoutes()
        val fixture = trackedProject("settings-outsider")
        val outsider = createExtraUser()

        val response = client.get("/worlds/${fixture.worldId}/projects/${fixture.projectId}/settings") {
            addAuthCookie(this, outsider)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `switching on from the settings page follows the chests and redraws the section`() = testApplication {
        routing {
            install(AuthPlugin)
            apiV1Routes()
            with(WorldHandler()) { worldRoutes() }
        }
        val fixture = trackedProject("settings-on")
        setCollected(fixture.projectId, iron, 500)
        push(fixture.reporterToken, fixture.chest, iron to 12L)

        val response = client.patch(
            "/worlds/${fixture.worldId}/projects/${fixture.projectId}/settings/storage-tracked"
        ) {
            addAuthCookie(this)
            header("HX-Request", "true")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("tracked=true")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "Counted from chests.")
        assertTrue(trackingOf(fixture.projectId).tracked)
        assertEquals(12, collectedOf(fixture.projectId, iron))
    }

    @Test
    fun `switching on with nothing tagged is refused with a message for the switch`() = testApplication {
        installWorldRoutes()
        val worldId = createWorld("settings-untagged")
        val projectId = createProject(worldId)

        val response = client.patch("/worlds/$worldId/projects/$projectId/settings/storage-tracked") {
            addAuthCookie(this)
            header("HX-Request", "true")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("tracked=true")
        }

        assertTrue(response.status.value in 400..499, "expected a refusal, got ${response.status}")
        assertContains(response.bodyAsText(), "storageTracked")
        assertFalse(trackingOf(projectId).tracked)
    }

    @Test
    fun `switching with no value is refused`() = testApplication {
        installWorldRoutes()
        val fixture = trackedProject("settings-garbage")

        val response = client.patch(
            "/worlds/${fixture.worldId}/projects/${fixture.projectId}/settings/storage-tracked"
        ) {
            addAuthCookie(this)
            header("HX-Request", "true")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("tracked=maybe")
        }

        assertTrue(response.status.value in 400..499, "expected a refusal, got ${response.status}")
        assertFalse(trackingOf(fixture.projectId).tracked)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun addWorldMember(worldId: Int, member: TokenProfile, role: Role) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO world_members (user_id, world_id, display_name, world_role) VALUES (?, ?, ?, ?)"
            ),
            parameterSetter = { st, _ ->
                st.setInt(1, member.id); st.setInt(2, worldId)
                st.setString(3, member.minecraftUsername); st.setInt(4, role.level)
            }
        ).process(Unit)
    }

    private data class Fixture(
        val worldId: Int,
        val projectId: Int,
        val gatheringId: Int,
        val reporterToken: String,
        val chest: Long,
    )

    /** A project with one iron target and one tagged chest — everything but the switch. */
    private fun trackedProject(name: String, required: Int = 512): Fixture {
        val worldId = createWorld(name)
        val projectId = createProject(worldId)
        val gatheringId = insertGathering(projectId, iron, required)
        return Fixture(worldId, projectId, gatheringId, mintReporterToken(worldId), tagContainer(worldId, projectId))
    }

    private fun trackOn(projectId: Int) {
        val result = runBlocking { SetStorageTrackedStep.process(SetStorageTrackedInput(projectId, true)) }
        assertIs<Result.Success<*>>(result, "switching on failed: $result")
        assertTrue(trackingOf(projectId).tracked)
    }

    private fun trackingOf(projectId: Int) = runBlocking {
        (GetStorageTrackingStep.process(projectId) as Result.Success).value
    }

    private fun ApplicationTestBuilder.installWorldRoutes() {
        routing {
            install(AuthPlugin)
            with(WorldHandler()) { worldRoutes() }
        }
    }

    private suspend fun ApplicationTestBuilder.push(
        reporterToken: String,
        containerId: Long,
        vararg items: Pair<String, Long>,
    ) {
        val body = items.joinToString(",") { """{"item_id":"${it.first}","count":${it.second}}""" }
        val response = client.post("/api/v1/reporter/contents") {
            header("Authorization", "Bearer $reporterToken")
            contentType(ContentType.Application.Json)
            setBody(
                """{"containers":[{"id":$containerId,"state":"ok",
                    "seen_at":"2026-10-09T10:00:00Z","items":[$body]}]}""".trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private fun collectedOf(projectId: Int, itemId: String): Int =
        Database.getConnection().use { conn ->
            conn.prepareStatement(
                "SELECT collected FROM resource_gathering_progress WHERE project_id = ? AND item_id = ?"
            ).use { st ->
                st.setInt(1, projectId); st.setString(2, itemId)
                st.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
        }

    private fun progressRowExists(projectId: Int, itemId: String): Boolean =
        Database.getConnection().use { conn ->
            conn.prepareStatement(
                "SELECT 1 FROM resource_gathering_progress WHERE project_id = ? AND item_id = ?"
            ).use { st ->
                st.setInt(1, projectId); st.setString(2, itemId)
                st.executeQuery().use { rs -> rs.next() }
            }
        }

    private fun progressSourceOf(projectId: Int, itemId: String): String? =
        Database.getConnection().use { conn ->
            conn.prepareStatement(
                "SELECT progress_source FROM resource_gathering_progress WHERE project_id = ? AND item_id = ?"
            ).use { st ->
                st.setInt(1, projectId); st.setString(2, itemId)
                st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        }

    private fun setCollected(projectId: Int, itemId: String, value: Int) = runBlocking {
        UpsertProgressByItemStep.process(UpsertProgressByItemInput(projectId, itemId, value, value.toLong()))
    }

    private fun insertGathering(projectId: Int, itemId: String, required: Int): Int = runBlocking {
        (DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required) VALUES (?, ?, ?, ?) RETURNING id"
            ),
            parameterSetter = { st, _ ->
                st.setInt(1, projectId); st.setString(2, itemId)
                st.setString(3, itemId.substringAfter(':')); st.setInt(4, required)
            }
        ).process(Unit) as Result.Success).value
    }

    private fun mintReporterToken(worldId: Int): String = runBlocking {
        val token = ApiCrypto.newToken()
        CreateReporterTokenStep.process(
            CreateReporterTokenInput(worldId, ApiCrypto.sha256Hex(token), "test reporter", user.id)
        )
        token
    }

    private fun issueApiToken(): String = runBlocking {
        val token = ApiCrypto.newToken()
        CreateApiTokenStep.process(CreateApiTokenInput(user.id, ApiCrypto.sha256Hex(token), "test", null))
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
                "INSERT INTO projects (name, world_id, description, type, stage, state, " +
                    "location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'BUILDING', 'RESOURCE_GATHERING', 'ACTIVE', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { st, _ ->
                st.setString(1, "Storage Tracked IT ${System.nanoTime()}")
                st.setInt(2, worldId)
            }
        ).process(Unit) as Result.Success).value
    }

    /**
     * A tagged chest the server has already read once (`ok`), which is what switching on needs. A
     * fresh tag is `unreadable` until the first sweep reaches it.
     */
    private fun tagContainer(worldId: Int, projectId: Int, state: String = "ok"): Long =
        Database.getConnection().use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO container_tags
                    (world_id, project_id, dimension, x, y, z, group_key, kind, tagged_by, state)
                VALUES (?, ?, 'minecraft:overworld', 0, 64, 0, '0,64,0', 'chest', ?, ?)
                RETURNING id
                """.trimIndent()
            ).use { st ->
                st.setInt(1, worldId); st.setInt(2, projectId); st.setInt(3, user.id); st.setString(4, state)
                st.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
            }
        }

    private fun deleteTag(tagId: Long) =
        Database.getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM container_tags WHERE id = ?").use { st ->
                st.setLong(1, tagId); st.executeUpdate()
            }
        }
}
