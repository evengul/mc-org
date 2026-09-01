package app.mcorg.presentation.handler.world

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.handler.WorldHandler
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The Worlds-page hero (MCO-468): the "Active projects" peek is a set of links straight
 * into each project, and the headline number is a per-state tally rather than a
 * completion percentage.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class WorldsPageHeroIT : WithUser() {

    @Test
    fun `each active-projects peek row links straight to its project`() = testApplication {
        val worldId = createWorld("Hero IT Peek World")
        val activeId = createProject(worldId, "Peek Active Project", state = "ACTIVE")
        val pendingId = createProject(worldId, "Peek Pending Project", state = "PENDING")

        installWorldRoutes()

        val body = getWorldsPage()

        assertContains(body, "Peek Active Project")
        assertContains(body, """href="/worlds/$worldId/projects/$activeId"""")
        assertContains(body, """href="/worlds/$worldId/projects/$pendingId"""")
    }

    @Test
    fun `hero counts projects by state, leaving shelved work out of the total`() = testApplication {
        val worldId = createWorld("Hero IT Tally World")
        createProject(worldId, "Tally Active", state = "ACTIVE")
        createProject(worldId, "Tally Pending One", state = "PENDING")
        createProject(worldId, "Tally Pending Two", state = "PENDING")
        createProject(worldId, "Tally Done", state = "DONE")
        // Shelved: counted nowhere, so the parts still sum to the total.
        createProject(worldId, "Tally Cancelled", state = "CANCELLED")
        createProject(worldId, "Tally Archived", state = "ARCHIVED")

        installWorldRoutes()

        val body = getWorldsPage()

        assertEquals("1 in flight · 2 queued · 1 done of 4", tallyText(body))
    }

    @Test
    fun `hero shows no completion percentage or progress bar`() = testApplication {
        val worldId = createWorld("Hero IT No Percent World")
        createProject(worldId, "No Percent Project", state = "ACTIVE")

        installWorldRoutes()

        val body = getWorldsPage()

        assertFalse(body.contains("progressbar"), "hero still renders a progress bar")
        assertFalse(body.contains("% overall"), "hero still renders a completion percentage")
    }

    @Test
    fun `hero offers a new-project nudge aimed at the pick-a-door menu`() = testApplication {
        val worldId = createWorld("Hero IT Nudge World")
        createProject(worldId, "Nudge Project", state = "ACTIVE")

        installWorldRoutes()

        val body = getWorldsPage()

        assertContains(body, """href="/worlds/$worldId/projects#new"""")
        assertContains(body, "New project")
    }

    /** The tally's own text, tags stripped and whitespace collapsed. */
    private fun tallyText(body: String): String {
        val start = body.indexOf("""<div class="world-tally">""")
        check(start >= 0) { "no .world-tally rendered on the worlds page" }
        val end = body.indexOf("</div>", start)
        return body.substring(start, end)
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.getWorldsPage(): String {
        val response = client.get("/worlds") { addAuthCookie(this) }
        assertEquals(HttpStatusCode.OK, response.status)
        return response.bodyAsText()
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.installWorldRoutes() {
        routing {
            install(AuthPlugin)
            with(WorldHandler()) { worldRoutes() }
        }
    }

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = name,
                description = "test",
                version = MinecraftVersion.fromString("1.21.4")
            )
        )
        (result as Result.Success).value
    }

    private fun createProject(worldId: Int, name: String, state: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                        "VALUES (?, ?, '', 'BUILDING', 'IDEA', ?, 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
                stmt.setString(3, state)
            }
        ).process(Unit)
        (result as Result.Success).value
    }
}
