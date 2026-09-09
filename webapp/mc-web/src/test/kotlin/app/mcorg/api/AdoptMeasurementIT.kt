package app.mcorg.api

import app.mcorg.config.Database
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.resources.AdoptAllMeasurementsStep
import app.mcorg.pipeline.resources.AdoptMeasurementInput
import app.mcorg.pipeline.resources.AdoptMeasurementStep
import app.mcorg.pipeline.resources.GetProjectMeasurementsStep
import app.mcorg.pipeline.resources.commonsteps.UpsertProgressByItemInput
import app.mcorg.pipeline.resources.commonsteps.UpsertProgressByItemStep
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
import kotlin.test.assertIs

/**
 * Adopting a measurement, and the line that must never be crossed (MCO-539).
 *
 * The load-bearing property is the negative one: **a sweep alone never changes `collected`.** You
 * have 500 iron and you type 500. Then you tag your first chest and the sweep says 12. A design
 * where the measurement wins has just destroyed a correct number with an incomplete one, and it
 * would do it again on every project where tagging is not finished — which is all of them, for a
 * while. So the two numbers sit side by side until a human says otherwise, and adoption is the
 * only bridge.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class AdoptMeasurementIT : WithUser() {

    private val iron = "minecraft:iron_ingot"
    private val gold = "minecraft:gold_ingot"

    @Test
    fun `a sweep alone never changes what a human typed`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("no-overwrite")
        val projectId = createProject(worldId)
        insertGathering(projectId, iron, required = 512)
        setCollected(projectId, iron, 500)

        val reporterToken = mintReporterToken(worldId)
        val chest = tagContainer(worldId, projectId, 0, 64, 0)
        push(reporterToken, chest, iron to 12L)

        // The whole design in one assertion. The measurement is recorded and disagrees, loudly,
        // and `collected` is untouched.
        assertEquals(500, collectedOf(projectId, iron))
        assertEquals(12, measurementOf(projectId, iron)?.measured)
    }

    @Test
    fun `adopting sets collected to the measurement and stamps it as the mod's`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("adopt-one")
        val projectId = createProject(worldId)
        insertGathering(projectId, iron, required = 512)
        setCollected(projectId, iron, 500)
        val reporterToken = mintReporterToken(worldId)
        push(reporterToken, tagContainer(worldId, projectId, 0, 64, 0), iron to 12L)

        adopt(projectId, iron)

        assertEquals(12, collectedOf(projectId, iron))
        // Stamped, so a later web edit is distinguishable from the mod's number — the same field
        // MCO-284 uses to say who last set a count.
        assertEquals("mod", progressSourceOf(projectId, iron))
    }

    @Test
    fun `adopting is capped at required, like every other write to collected`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("adopt-cap")
        val projectId = createProject(worldId)
        insertGathering(projectId, iron, required = 64)
        val reporterToken = mintReporterToken(worldId)
        push(reporterToken, tagContainer(worldId, projectId, 0, 64, 0), iron to 640L)

        adopt(projectId, iron)

        // A stash of ten stacks against a requirement of one does not make progress 640/64.
        assertEquals(64, collectedOf(projectId, iron))
    }

    @Test
    fun `adopting one item leaves the others alone`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("adopt-scope")
        val projectId = createProject(worldId)
        insertGathering(projectId, iron, required = 512)
        insertGathering(projectId, gold, required = 512)
        setCollected(projectId, gold, 300)
        val reporterToken = mintReporterToken(worldId)
        push(reporterToken, tagContainer(worldId, projectId, 0, 64, 0), iron to 12L, gold to 7L)

        adopt(projectId, iron)

        assertEquals(12, collectedOf(projectId, iron))
        assertEquals(300, collectedOf(projectId, gold), "adopting iron moved gold")
    }

    @Test
    fun `adopt all takes the lot, and reports how many rows it wrote`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("adopt-all")
        val projectId = createProject(worldId)
        insertGathering(projectId, iron, required = 512)
        insertGathering(projectId, gold, required = 512)
        setCollected(projectId, iron, 500)
        setCollected(projectId, gold, 300)
        val reporterToken = mintReporterToken(worldId)
        push(reporterToken, tagContainer(worldId, projectId, 0, 64, 0), iron to 12L, gold to 7L)

        val written = runBlocking { AdoptAllMeasurementsStep.process(projectId) }

        assertEquals(12, collectedOf(projectId, iron))
        assertEquals(7, collectedOf(projectId, gold))
        // The count is what lets the button say what it will change rather than claiming success
        // after rewriting an unknown number of someone's numbers.
        assertEquals(2, (written as Result.Success).value)
    }

    @Test
    fun `adopt all on a project with no measurements changes nothing`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("adopt-none")
        val projectId = createProject(worldId)
        insertGathering(projectId, iron, required = 512)
        setCollected(projectId, iron, 500)

        runBlocking { AdoptAllMeasurementsStep.process(projectId) }

        assertEquals(500, collectedOf(projectId, iron), "an empty adopt zeroed a typed number")
    }

    /**
     * Adopt, and insist it actually wrote.
     *
     * Without the assertion a failed write reads as "the number did not change", which is what
     * three of these tests said while the SQL was malformed — a silently passing test for a
     * silently broken statement.
     */
    private fun adopt(projectId: Int, itemId: String) {
        val result = runBlocking { AdoptMeasurementStep.process(AdoptMeasurementInput(projectId, itemId)) }
        assertIs<Result.Success<*>>(result, "the adopt did not write: $result")
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

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
                    "seen_at":"2026-09-09T10:00:00Z","items":[$body]}]}""".trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private fun measurementOf(projectId: Int, itemId: String) = runBlocking {
        GetProjectMeasurementsStep.process(projectId).getOrNull()?.get(itemId)
    }

    private fun collectedOf(projectId: Int, itemId: String): Int = readInt(
        "SELECT collected FROM resource_gathering_progress WHERE project_id = ? AND item_id = ?",
        projectId, itemId,
    ) ?: 0

    private fun progressSourceOf(projectId: Int, itemId: String): String? =
        Database.getConnection().use { conn ->
            conn.prepareStatement(
                "SELECT progress_source FROM resource_gathering_progress WHERE project_id = ? AND item_id = ?"
            ).use { st ->
                st.setInt(1, projectId); st.setString(2, itemId)
                st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        }

    private fun readInt(sql: String, projectId: Int, itemId: String): Int? =
        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { st ->
                st.setInt(1, projectId); st.setString(2, itemId)
                st.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
            }
        }

    private fun setCollected(projectId: Int, itemId: String, value: Int) = runBlocking {
        UpsertProgressByItemStep.process(
            UpsertProgressByItemInput(projectId, itemId, value, value.toLong())
        )
    }

    private fun insertGathering(projectId: Int, itemId: String, required: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required) VALUES (?, ?, ?, ?) RETURNING id"
            ),
            parameterSetter = { st, _ ->
                st.setInt(1, projectId); st.setString(2, itemId)
                st.setString(3, itemId.substringAfter(':')); st.setInt(4, required)
            }
        ).process(Unit)
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
                "INSERT INTO projects (name, world_id, description, type, stage, state, " +
                    "location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'BUILDING', 'RESOURCE_GATHERING', 'ACTIVE', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { st, _ ->
                st.setString(1, "Adopt IT Project ${System.nanoTime()}")
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
}
