package app.mcorg.presentation.handler.world

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.user.Role
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.pipeline.world.roadmap.handleGetWorldRoadmap
import app.mcorg.pipeline.world.roadmap.ordering.GetManualOrderingsStep
import app.mcorg.pipeline.world.roadmap.ordering.handleAddManualOrdering
import app.mcorg.pipeline.world.roadmap.ordering.handleDeleteManualOrdering
import app.mcorg.pipeline.world.roadmap.ordering.handleGetOrderingEditForm
import app.mcorg.pipeline.world.roadmap.ordering.handleGetOrderingForm
import app.mcorg.pipeline.world.roadmap.ordering.handlePickOrderingProject
import app.mcorg.pipeline.world.roadmap.ordering.handleSearchOrderingCandidates
import app.mcorg.pipeline.world.roadmap.ordering.handleUpdateManualOrdering
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
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
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.server.routing.get
import io.ktor.server.routing.method
import io.ktor.server.routing.post
import io.ktor.server.routing.route
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
import kotlin.test.assertTrue

/**
 * The manual ordering editor (MCO-302), end to end.
 *
 * `project_dependencies` had read paths, a cache, a param plugin and a single writer — the
 * idea import — and no way at all to remove a row. These tests cover the writer that was
 * missing, and in particular the delete: an import could previously assert an ordering that
 * nothing short of deleting the project could take back.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ManualOrderingIT : WithUser() {

    private val version = MinecraftVersion.Release(1, 21, 4)

    // ---- the roster on the page ---------------------------------------------------------

    @Test
    fun `the roadmap carries the section even when nothing has been ordered by hand`() = testApplication {
        setupRoutes()
        val world = createWorld("Empty roster world")
        createProject(world, "Perimeter")
        createProject(world, "Wither farm")

        val body = client.get("/worlds/$world/roadmap") { addAuthCookie(this) }.bodyAsText()

        // The trap the first attempt fell into: an affordance that renders only once a row
        // exists is unreachable for the case the feature is *for*.
        assertContains(body, "MANUAL ORDERING · 0")
        assertContains(body, "Add ordering")
        assertContains(body, "Nothing here yet")
        assertContains(body, "Removing a generated edge isn't possible here")
    }

    @Test
    fun `an ordering somebody recorded is listed with its reason`() = testApplication {
        setupRoutes()
        val world = createWorld("Roster world")
        val perimeter = createProject(world, "Perimeter")
        val walls = createProject(world, "Walls")
        insertOrdering(first = perimeter, then = walls, reason = "The hole has to be dug first.")

        val body = client.get("/worlds/$world/roadmap") { addAuthCookie(this) }.bodyAsText()

        assertContains(body, "MANUAL ORDERING · 1")
        assertContains(body, "The hole has to be dug first.")
        assertContains(body, "Perimeter")
        assertContains(body, "Walls")
    }

    // ---- adding ---------------------------------------------------------------------------

    @Test
    fun `adding an ordering stores it and sends the reader back to the roadmap`() = testApplication {
        setupRoutes()
        val world = createWorld("Add world")
        val perimeter = createProject(world, "Perimeter")
        val walls = createProject(world, "Walls")

        val response = postOrdering(world, perimeter, walls, "No material passes — dig first.")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/worlds/$world/roadmap", response.headers["Location"])

        val stored = orderings(world).single()
        // Direction is the whole ballgame: `first` is the prerequisite, so it is the row's
        // depends_on side. Reversing it silently inverts the graph.
        assertEquals("Perimeter", stored.firstProjectName)
        assertEquals("Walls", stored.thenProjectName)
        assertEquals("No material passes — dig first.", stored.reason)
    }

    @Test
    fun `an ordering with no reason is refused`() = testApplication {
        setupRoutes()
        val world = createWorld("No reason world")
        val perimeter = createProject(world, "Perimeter")
        val walls = createProject(world, "Walls")

        val response = postOrdering(world, perimeter, walls, "   ")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "Say why this ordering exists")
        assertTrue(orderings(world).isEmpty())
    }

    @Test
    fun `a project cannot be ordered before itself`() = testApplication {
        setupRoutes()
        val world = createWorld("Self world")
        val perimeter = createProject(world, "Perimeter")

        val response = postOrdering(world, perimeter, perimeter, "nonsense")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "cannot come before itself")
        assertTrue(orderings(world).isEmpty())
    }

    @Test
    fun `the same pair cannot be ordered twice`() = testApplication {
        setupRoutes()
        val world = createWorld("Duplicate world")
        val perimeter = createProject(world, "Perimeter")
        val walls = createProject(world, "Walls")
        insertOrdering(first = perimeter, then = walls, reason = "Dig first.")

        val response = postOrdering(world, perimeter, walls, "Dig first, again.")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "already ordered")
        assertEquals(1, orderings(world).size)
    }

    @Test
    fun `an ordering that would close a loop is refused`() = testApplication {
        setupRoutes()
        val world = createWorld("Loop world")
        val perimeter = createProject(world, "Perimeter")
        val walls = createProject(world, "Walls")
        insertOrdering(first = perimeter, then = walls, reason = "Dig first.")

        // The reverse: walls before perimeter, which closes the pair into a loop.
        val response = postOrdering(world, walls, perimeter, "Backwards.")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "would make a loop")
        assertEquals(1, orderings(world).size)
    }

    @Test
    fun `a project from another world cannot be ordered into this one`() = testApplication {
        setupRoutes()
        val world = createWorld("This world")
        val elsewhere = createWorld("Other world")
        val here = createProject(world, "Perimeter")
        val there = createProject(elsewhere, "Somebody else's build")

        val response = postOrdering(world, here, there, "Should not be possible.")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "belong to this world")
        assertTrue(orderings(world).isEmpty())
    }

    // ---- removing ---------------------------------------------------------------------------

    @Test
    fun `an ordering an idea import asserted can be removed`() = testApplication {
        setupRoutes()
        // The production shape this issue exists for: an imported idea wrote a dependency, and
        // until this endpoint landed nothing in the app could take it back.
        val world = createWorld("Import world")
        val slime = createProject(world, "New slime farm")
        val ghast = createProject(world, "Ghast Farm")
        insertOrdering(first = slime, then = ghast, reason = null, declaredBy = "IDEA_IMPORT")
        val id = orderings(world).single().id

        val body = client.get("/worlds/$world/roadmap") { addAuthCookie(this) }.bodyAsText()
        assertContains(body, "Came in with an imported idea — no reason recorded.")

        val response = client.delete("/worlds/$world/roadmap/ordering/$id") {
            addAuthCookie(this)
            header("HX-Request", "true")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("/worlds/$world/roadmap", response.headers["HX-Redirect"])
        assertTrue(orderings(world).isEmpty())
    }

    @Test
    fun `an ordering in another world cannot be removed through this one`() = testApplication {
        setupRoutes()
        val world = createWorld("Delete guard world")
        val elsewhere = createWorld("Delete guard other world")
        val a = createProject(elsewhere, "A")
        val b = createProject(elsewhere, "B")
        insertOrdering(first = a, then = b, reason = "Theirs, not yours.")
        val id = orderings(elsewhere).single().id

        val response = client.delete("/worlds/$world/roadmap/ordering/$id") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(1, orderings(elsewhere).size)
    }

    // ---- editing ------------------------------------------------------------------------------

    @Test
    fun `editing rewrites the reason and claims the row for the editor`() = testApplication {
        setupRoutes()
        val world = createWorld("Edit world")
        val slime = createProject(world, "New slime farm")
        val ghast = createProject(world, "Ghast Farm")
        insertOrdering(first = slime, then = ghast, reason = null, declaredBy = "IDEA_IMPORT")
        val id = orderings(world).single().id

        val form = client.get("/worlds/$world/roadmap/ordering/$id/edit") { addAuthCookie(this) }
        assertEquals(HttpStatusCode.OK, form.status)
        val formBody = form.bodyAsText()
        assertContains(formBody, "Save changes")
        // The pair is fixed in update mode — a different pair is a different edge.
        assertFalse(formBody.contains("DO THIS FIRST"), "the pickers are not offered when editing")

        val response = createClient { followRedirects = false }
            .post("/worlds/$world/roadmap/ordering/$id") {
                addAuthCookie(this)
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(Parameters.build { append("reason", "Slime chunk has to be cleared first.") }.formUrlEncode())
            }

        assertEquals(HttpStatusCode.Found, response.status)
        val stored = orderings(world).single()
        assertEquals("Slime chunk has to be cleared first.", stored.reason)
        assertEquals("EDITOR", declaredByOf(stored.id), "somebody has now explained it")
    }

    @Test
    fun `an edit that empties the reason is refused`() = testApplication {
        setupRoutes()
        val world = createWorld("Edit refusal world")
        val perimeter = createProject(world, "Perimeter")
        val walls = createProject(world, "Walls")
        insertOrdering(first = perimeter, then = walls, reason = "Dig first.")
        val id = orderings(world).single().id

        val response = client.post("/worlds/$world/roadmap/ordering/$id") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(Parameters.build { append("reason", "") }.formUrlEncode())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Dig first.", orderings(world).single().reason, "the stored reason survives")
    }

    // ---- the comboboxes -------------------------------------------------------------------------

    @Test
    fun `search matches on name across every state and says how many of the world matched`() = testApplication {
        setupRoutes()
        val world = createWorld("Search world")
        createProject(world, "Slime farm", state = ProjectState.DONE)
        createProject(world, "New slime farm")
        createProject(world, "Wither farm")

        val body = client.get("/worlds/$world/roadmap/ordering/search?field=first&firstQuery=slime") {
            addAuthCookie(this)
        }.bodyAsText()

        assertContains(body, "Slime farm")
        assertContains(body, "New slime farm")
        assertFalse(body.contains("Wither farm"))
        assertContains(body, "2 of 3 projects match")
        // A finished farm is a legitimate prerequisite, so it is offered — and its state is on
        // the row, because picking the wrong one of two similar names is undetectable later.
        assertContains(body, "✓ done")
        assertContains(body, "pending")
    }

    @Test
    fun `a project that would close a loop is marked rather than hidden`() = testApplication {
        setupRoutes()
        val world = createWorld("Marked search world")
        val perimeter = createProject(world, "Perimeter")
        val walls = createProject(world, "Walls")
        insertOrdering(first = perimeter, then = walls, reason = "Dig first.")

        // Searching for the prerequisite while `Perimeter` is already the dependent: putting
        // Walls first would close the loop.
        val body = client.get(
            "/worlds/$world/roadmap/ordering/search?field=first&thenId=$perimeter&firstQuery=Walls"
        ) { addAuthCookie(this) }.bodyAsText()

        assertContains(body, "Walls")
        assertContains(body, "would cycle")
        // Marked, not dropped — a search that silently loses the project you typed sends you
        // round the same loop again.
        assertFalse(body.contains("rmg-combo__row--pick"), "the only match is unpickable")
    }

    @Test
    fun `picking a project fills the form in without losing the reason already typed`() = testApplication {
        setupRoutes()
        val world = createWorld("Pick world")
        val perimeter = createProject(world, "Perimeter")

        val body = client.get(
            "/worlds/$world/roadmap/ordering/pick?field=first&projectId=$perimeter&reason=Half+typed"
        ) { addAuthCookie(this) }.bodyAsText()

        assertContains(body, """name="firstId" value="$perimeter"""")
        assertContains(body, "Perimeter")
        assertContains(body, "Half typed")
    }

    @Test
    fun `the form opens and closes`() = testApplication {
        setupRoutes()
        val world = createWorld("Form world")
        createProject(world, "Perimeter")

        val open = client.get("/worlds/$world/roadmap/ordering/form") { addAuthCookie(this) }.bodyAsText()
        assertContains(open, "DO THIS FIRST")
        assertContains(open, "BEFORE THIS")
        assertContains(open, "WHY — REQUIRED")

        val closed = client.get("/worlds/$world/roadmap/ordering/form?close=true") {
            addAuthCookie(this)
        }.bodyAsText()
        assertFalse(closed.contains("DO THIS FIRST"))
        assertContains(closed, "rmg-ordering-form")
    }

    // ---- authorization ------------------------------------------------------------------------------

    @Test
    fun `a plain member cannot order the world's projects`() = testApplication {
        setupRoutes()
        val world = createWorld("Member world")
        val perimeter = createProject(world, "Perimeter")
        val walls = createProject(world, "Walls")
        val member = createExtraUser()
        addWorldMember(member.id, world, Role.MEMBER)

        val write = client.post("/worlds/$world/roadmap/ordering") {
            addAuthCookie(this, member)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(orderingForm(perimeter, walls, "Not mine to decide."))
        }
        assertEquals(HttpStatusCode.Forbidden, write.status)
        assertTrue(orderings(world).isEmpty())

        // And the roster renders for them read-only: the roadmap is a members' page, but the
        // ordering is a world decision, so the affordances are the admin's.
        val page = client.get("/worlds/$world/roadmap") { addAuthCookie(this, member) }.bodyAsText()
        assertContains(page, "MANUAL ORDERING · 0")
        assertFalse(page.contains("Add ordering"))
    }

    // ---- fixtures -------------------------------------------------------------------------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                get("/roadmap") { call.handleGetWorldRoadmap() }
                route("/roadmap/ordering") {
                    install(WorldAdminPlugin)
                    post { call.handleAddManualOrdering() }
                    get("/form") { call.handleGetOrderingForm() }
                    get("/search") { call.handleSearchOrderingCandidates() }
                    get("/pick") { call.handlePickOrderingProject() }
                    route("/{orderingId}") {
                        get("/edit") { call.handleGetOrderingEditForm() }
                        post { call.handleUpdateManualOrdering() }
                        method(HttpMethod.Delete) { handle { call.handleDeleteManualOrdering() } }
                    }
                }
            }
        }
    }

    /**
     * The test client follows redirects by default, which would swallow the 303 every write
     * answers with and hand back the roadmap page instead. These assertions are about the
     * redirect itself, so they need a client that stops at it.
     */
    private suspend fun ApplicationTestBuilder.postOrdering(
        worldId: Int,
        first: Int,
        then: Int,
        reason: String,
    ) = createClient { followRedirects = false }.post("/worlds/$worldId/roadmap/ordering") {
        addAuthCookie(this)
        contentType(ContentType.Application.FormUrlEncoded)
        setBody(orderingForm(first, then, reason))
    }

    private fun orderingForm(first: Int, then: Int, reason: String) = Parameters.build {
        append("firstId", first.toString())
        append("thenId", then.toString())
        append("reason", reason)
    }.formUrlEncode()

    private fun orderings(worldId: Int) = runBlocking {
        (GetManualOrderingsStep(worldId).process(Unit) as Result.Success).value
    }

    private fun declaredByOf(orderingId: Int): String? = runBlocking {
        DatabaseSteps.query<Unit, String?>(
            sql = SafeSQL.select("SELECT declared_by FROM project_dependencies WHERE id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, orderingId) },
            resultMapper = { if (it.next()) it.getString("declared_by") else null }
        ).process(Unit).getOrNull()
    }

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name = "$name ${System.nanoTime()}", description = "test", version = version)
        )
        (result as Result.Success).value
    }

    private fun createProject(
        worldId: Int,
        name: String,
        state: ProjectState = ProjectState.PENDING,
    ): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'FARMING', 'PLANNING', ?, 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
                stmt.setString(3, state.name)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    /** Writes a row the way the idea import does, so the tests exercise both provenances. */
    private fun insertOrdering(
        first: Int,
        then: Int,
        reason: String?,
        declaredBy: String = "EDITOR",
    ) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO project_dependencies (project_id, depends_on_project_id, reason, declared_by) VALUES (?, ?, ?, ?)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, then)
                stmt.setInt(2, first)
                stmt.setString(3, reason)
                stmt.setString(4, declaredBy)
            }
        ).process(Unit)
    }

    private fun addWorldMember(userId: Int, worldId: Int, role: Role) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO world_members (user_id, world_id, display_name, world_role) VALUES (?, ?, ?, ?)"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, userId)
                stmt.setInt(2, worldId)
                stmt.setString(3, "member-$userId")
                stmt.setInt(4, role.level)
            }
        ).process(Unit)
        CacheManager.onMemberAdded(userId, worldId)
        CacheManager.worldMemberRole.asMap().keys
            .filter { it.startsWith("$userId:$worldId:") }
            .forEach { CacheManager.worldMemberRole.invalidate(it) }
    }
}
