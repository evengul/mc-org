package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.ImportQueue
import app.mcorg.pipeline.project.ReviewedMaterial
import app.mcorg.pipeline.project.ReviewedMaterialsCodec
import app.mcorg.pipeline.project.handleImportIdea
import app.mcorg.pipeline.project.handleReviewIdeaImport
import app.mcorg.pipeline.project.handleStartFarmSuggestionImport
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.IdeaParamPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * MCO-459 — importing several suggested designs without losing the plan you were reading.
 *
 * `ImportQueueTest` covers the queue arithmetic. This covers the two things only the real
 * doors can answer: that each step hands off to the next instead of to the project it just
 * made, and that a lone import is untouched by any of it.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class BatchImportWizardIT : WithUser() {

    private val version = MinecraftVersion.Release(1, 21, 4)

    private var worldId: Int = 0
    private var planProjectId: Int = 0
    private var first: Int = 0
    private var second: Int = 0
    private var third: Int = 0
    private var unselected: Int = 0

    @BeforeAll
    fun setup() {
        seedItems(listOf("minecraft:oak_planks" to "Oak Planks", "minecraft:iron_ingot" to "Iron Ingot"))

        worldId = createWorld("Batch import world")
        planProjectId = createProject(worldId, "Storage System")

        first = createIdea("First Farm")
        addRequirement(first, "minecraft:oak_planks", 32)
        second = createIdea("Second Farm")
        addRequirement(second, "minecraft:oak_planks", 16)
        third = createIdea("Third Farm")
        addRequirement(third, "minecraft:oak_planks", 8)
        unselected = createIdea("Unselected Farm")
        addRequirement(unselected, "minecraft:oak_planks", 4)
    }

    // ---- opening the batch ---------------------------------------------------------

    @Test
    fun `selecting designs opens the first review carrying the whole queue`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }
        val before = countProjects()

        val response = client.post(batchUrl()) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("design=$first&design=$second&design=$third")
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val location = response.headers["Location"]!!
        assertContains(location, "/ideas/$first/import/review")
        assertContains(location, "worldId=$worldId")
        assertContains(location, "queue=$first,$second,$third")
        assertContains(location, "returnTo=$planProjectId")
        assertEquals(before, countProjects(), "opening the wizard creates nothing")
    }

    @Test
    fun `submitting with nothing selected goes back to the plan`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        val response = client.post(batchUrl()) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("")
        }

        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertEquals("/worlds/$worldId/projects/$planProjectId", response.headers["Location"])
    }

    // ---- the review step knows where it is -----------------------------------------

    @Test
    fun `the review says which design of how many this is`() = testApplication {
        setupRoutes()

        val body = client.get(reviewUrl(second, listOf(first, second, third))) { addAuthCookie(this) }.bodyAsText()

        assertContains(body, "Review 2 of 3")
        assertContains(body, "Skip this one")
        assertContains(body, "Done, back to plan")
        assertContains(body, "Create project &amp; next")
        // The POST reads the queue back off the form, so it has to travel in the form too.
        assertContains(body, """name="${ImportQueue.QUEUE_PARAM}"""")
        assertContains(body, """name="${ImportQueue.RETURN_PARAM}"""")
    }

    @Test
    fun `the last step offers no skip and promises no next`() = testApplication {
        setupRoutes()

        val body = client.get(reviewUrl(third, listOf(first, second, third))) { addAuthCookie(this) }.bodyAsText()

        assertContains(body, "Review 3 of 3")
        assertFalse(body.contains("Skip this one"), "there is nothing after this one to skip to")
        assertFalse(body.contains("&amp; next"), "the button must not promise a step that does not exist")
        assertContains(body, "Done, back to plan")
    }

    @Test
    fun `a design the batch never selected reviews as a plain single import`() = testApplication {
        setupRoutes()

        // A hand-edited URL. Rendering "Review 0 of 3" would be worse than ignoring the queue.
        val body = client.get(reviewUrl(unselected, listOf(first, second))) { addAuthCookie(this) }.bodyAsText()

        assertFalse(body.contains("Review 1 of"), body.substringAfter("import-review__title").take(200))
        assertFalse(body.contains("Done, back to plan"))
        assertContains(body, "Cancel", message = "it falls back to the ordinary review screen")
    }

    @Test
    fun `an ordinary review is untouched by the wizard`() = testApplication {
        setupRoutes()

        val body = client.get("/ideas/$first/import/review?worldId=$worldId") { addAuthCookie(this) }.bodyAsText()

        assertFalse(body.contains("Review 1 of"))
        assertContains(body, "Create project")
        assertContains(body, "Cancel")
    }

    // ---- creating hands off to the next step ---------------------------------------

    @Test
    fun `creating mid-batch lands on the next design, not the project just made`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        val response = client.post("/ideas/$first/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "worldId=$worldId&name=First Farm&" +
                    queueFields(listOf(first, second, third)) + "&" +
                    materials("minecraft:oak_planks" to 32)
            )
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val location = response.headers["Location"]!!
        assertContains(location, "/ideas/$second/import/review")
        assertContains(location, "queue=$first,$second,$third", message = "the queue survives the hop")
        // It really did create — this is a hand-off, not a detour.
        assertEquals(1, countProjectsNamed("First Farm"))
    }

    @Test
    fun `the last create lands back on the plan the batch started from`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        val response = client.post("/ideas/$third/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "worldId=$worldId&name=Third Farm&" +
                    queueFields(listOf(first, second, third)) + "&" +
                    materials("minecraft:oak_planks" to 8)
            )
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        assertEquals("/worlds/$worldId/projects/$planProjectId", response.headers["Location"])
        assertEquals(1, countProjectsNamed("Third Farm"))
    }

    @Test
    fun `a lone import still lands on the project it just made`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        // MCO-457's rule, and the reason the queue is optional rather than always-on.
        val response = client.post("/ideas/$second/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("worldId=$worldId&name=Lone Farm&" + materials("minecraft:oak_planks" to 16))
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val location = response.headers["Location"]!!
        val projectId = location.substringAfterLast("/").toInt()
        assertEquals("/worlds/$worldId/projects/$projectId", location)
        assertFalse(projectId == planProjectId, "it lands on the new project, not the plan")
    }

    // ---- routing --------------------------------------------------------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/ideas/{ideaId}") {
                install(IdeaParamPlugin)
                route("/import") {
                    get("/review") { call.handleReviewIdeaImport() }
                    post { call.handleImportIdea() }
                }
            }
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    post("/farm-suggestions/import") { call.handleStartFarmSuggestionImport() }
                }
            }
        }
    }

    private fun batchUrl() = "/worlds/$worldId/projects/$planProjectId/farm-suggestions/import"

    private fun reviewUrl(ideaId: Int, queue: List<Int>) =
        "/ideas/$ideaId/import/review?worldId=$worldId" +
            "&${ImportQueue.QUEUE_PARAM}=${queue.joinToString(",")}" +
            "&${ImportQueue.RETURN_PARAM}=$planProjectId"

    private fun queueFields(queue: List<Int>) =
        "${ImportQueue.QUEUE_PARAM}=${queue.joinToString(",")}&${ImportQueue.RETURN_PARAM}=$planProjectId"

    private fun materials(vararg rows: Pair<String, Int>): String {
        val encoded = ReviewedMaterialsCodec.encode(
            rows.map { (id, amount) -> ReviewedMaterial(id, amount, included = true) }
        )
        return "${ReviewedMaterialsCodec.FIELD}=$encoded"
    }

    // ---- fixtures --------------------------------------------------------------------

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name = name, description = "test", version = version)
        )
        (result as Result.Success).value
    }

    private fun createProject(worldId: Int, name: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'BUILDING', 'PLANNING', ?, 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
                stmt.setString(3, ProjectState.ACTIVE.name)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun seedItems(items: List<Pair<String, String>>) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO minecraft_version (version) VALUES ('1.21.4') ON CONFLICT DO NOTHING"),
            parameterSetter = { _, _ -> }
        ).process(Unit)
        DatabaseSteps.batchUpdate<Pair<String, String>>(
            SafeSQL.insert(
                "INSERT INTO minecraft_items (version, item_id, item_name) VALUES ('1.21.4', ?, ?) ON CONFLICT DO NOTHING"
            ),
            parameterSetter = { stmt, (itemId, itemName) ->
                stmt.setString(1, itemId)
                stmt.setString(2, itemName)
            }
        ).process(items)
    }

    private fun createIdea(name: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                """
                INSERT INTO ideas (name, description, category, author, difficulty, minecraft_version_range, category_data, created_by)
                VALUES (?, 'test idea', 'FARM', '{"type":"single","name":"tester"}'::jsonb, 'EASY',
                        '{"type":"app.mcorg.domain.model.minecraft.MinecraftVersionRange.Unbounded"}'::jsonb, '{}'::jsonb, ?)
                RETURNING id
                """.trimIndent()
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, user.id)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun addRequirement(ideaId: Int, itemId: String, quantity: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO idea_item_requirements (idea_id, item_id, quantity) VALUES (?, ?, ?)"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, ideaId)
                stmt.setString(2, itemId)
                stmt.setInt(3, quantity)
            }
        ).process(Unit)
    }

    private fun countProjects(): Int = runBlocking {
        val result = DatabaseSteps.query<Int, Int>(
            sql = SafeSQL.select("SELECT COUNT(*) FROM projects WHERE world_id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs -> if (rs.next()) rs.getInt(1) else 0 }
        ).process(worldId)
        (result as Result.Success).value
    }

    private fun countProjectsNamed(name: String): Int = runBlocking {
        val result = DatabaseSteps.query<String, Int>(
            sql = SafeSQL.select("SELECT COUNT(*) FROM projects WHERE world_id = ? AND name = ?"),
            parameterSetter = { stmt, n ->
                stmt.setInt(1, worldId)
                stmt.setString(2, n)
            },
            resultMapper = { rs -> if (rs.next()) rs.getInt(1) else 0 }
        ).process(name)
        (result as Result.Success).value
    }
}
