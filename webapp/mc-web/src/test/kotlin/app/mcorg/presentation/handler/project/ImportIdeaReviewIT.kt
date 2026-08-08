package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.Role
import app.mcorg.config.CacheManager
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.handleImportIdea
import app.mcorg.pipeline.project.handleReviewIdeaImport
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.IdeaParamPlugin
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
import kotlin.test.assertTrue

/**
 * Idea import through the review screen (MCO-306): the second import door gets the same
 * exclude-before-you-commit treatment as the schematic upload.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ImportIdeaReviewIT : WithUser() {

    private val version = MinecraftVersion.fromString("1.21.4")

    private var worldId: Int = 0
    private var ideaId: Int = 0

    @BeforeAll
    fun setup() {
        worldId = createWorld("Idea Import IT World")
        seedItem("minecraft:iron_ingot", "Iron Ingot")
        seedItem("minecraft:oak_planks", "Oak Planks")
        seedItem("minecraft:redstone", "Redstone Dust")
        ideaId = createIdea("Iron Farm Idea")
        addRequirement(ideaId, "minecraft:iron_ingot", 64)
        addRequirement(ideaId, "minecraft:oak_planks", 32)
        addRequirement(ideaId, "minecraft:redstone", 8)
    }

    // ---- review ------------------------------------------------------------------

    @Test
    fun `review lists the idea's requirements and creates nothing`() = testApplication {
        setupRoutes()
        val before = countProjects()

        val response = client.get("/ideas/$ideaId/import/review?worldId=$worldId") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Review this import")
        assertContains(body, "Iron Farm Idea")
        assertContains(body, "qty[minecraft:iron_ingot]")
        assertContains(body, "qty[minecraft:oak_planks]")
        // The form has to carry the world through — the action URL has no room for it.
        assertContains(body, "name=\"worldId\"")
        assertEquals(before, countProjects(), "review must not create anything")
    }

    @Test
    fun `review without a world is refused`() = testApplication {
        setupRoutes()

        val response = client.get("/ideas/$ideaId/import/review") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `a non-member cannot review an import into someone else's world`() = testApplication {
        setupRoutes()
        val outsider = createExtraUser("idea-review-outsider")

        val response = client.get("/ideas/$ideaId/import/review?worldId=$worldId") {
            addAuthCookie(this, outsider)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // ---- create ------------------------------------------------------------------

    @Test
    fun `import creates the project from the reviewed rows only`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        val response = client.post("/ideas/$ideaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("worldId=$worldId&name=Reviewed Iron Farm&qty[minecraft:iron_ingot]=64&qty[minecraft:redstone]=8")
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val projectId = response.headers["Location"]!!.substringAfterLast("/").toInt()

        assertEquals("Reviewed Iron Farm", getProjectName(projectId))
        assertEquals(
            listOf("minecraft:iron_ingot" to 64, "minecraft:redstone" to 8),
            readRequirements(projectId),
            "oak planks were excluded on the review screen",
        )
        assertEquals(ideaId, getProjectIdeaId(projectId), "the idea link survives the review step")
    }

    @Test
    fun `a submission with no rows is refused rather than importing the whole idea`() = testApplication {
        setupRoutes()
        val before = countProjects()

        // Unchecking every row submits no qty[...] fields at all, which is byte-identical to
        // a bare direct post. Importing the idea's full list here would do the opposite of
        // what the user just asked for.
        val response = client.post("/ideas/$ideaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("worldId=$worldId")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(before, countProjects())
    }

    @Test
    fun `an item outside the world's catalog is refused`() = testApplication {
        setupRoutes()
        val before = countProjects()

        val response = client.post("/ideas/$ideaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("worldId=$worldId&qty[minecraft:not_a_real_item]=1")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(before, countProjects())
    }

    @Test
    fun `a non-member cannot import into someone else's world`() = testApplication {
        setupRoutes()
        val outsider = createExtraUser("idea-import-outsider")
        val before = countProjects()

        val response = client.post("/ideas/$ideaId/import") {
            addAuthCookie(this, outsider)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("worldId=$worldId&qty[minecraft:iron_ingot]=1")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(before, countProjects())
    }

    @Test
    fun `unauthenticated review is redirected`() = testApplication {
        setupRoutes()
        val unauth = createClient { followRedirects = false }

        val response = unauth.get("/ideas/$ideaId/import/review?worldId=$worldId")

        assertEquals(HttpStatusCode.Found, response.status)
    }

    // ---- routing — mirrors IdeaHandler --------------------------------------------

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
        }
    }

    // ---- fixtures ------------------------------------------------------------------

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name = name, description = "test", version = version)
        )
        (result as Result.Success).value
    }

    private fun seedItem(itemId: String, itemName: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO minecraft_version (version) VALUES ('1.21.4') ON CONFLICT DO NOTHING"),
            parameterSetter = { _, _ -> }
        ).process(Unit)
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO minecraft_items (version, item_id, item_name) VALUES ('1.21.4', ?, ?) ON CONFLICT DO NOTHING"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, itemId)
                stmt.setString(2, itemName)
            }
        ).process(Unit)
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
            SafeSQL.insert(
                "INSERT INTO idea_item_requirements (idea_id, item_id, quantity) VALUES (?, ?, ?)"
            ),
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

    private fun getProjectName(projectId: Int): String = runBlocking {
        val result = DatabaseSteps.query<Int, String>(
            sql = SafeSQL.select("SELECT name FROM projects WHERE id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs -> rs.next(); rs.getString("name") }
        ).process(projectId)
        (result as Result.Success).value
    }

    private fun getProjectIdeaId(projectId: Int): Int = runBlocking {
        val result = DatabaseSteps.query<Int, Int>(
            sql = SafeSQL.select("SELECT project_idea_id FROM projects WHERE id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs -> rs.next(); rs.getInt("project_idea_id") }
        ).process(projectId)
        (result as Result.Success).value
    }

    private fun readRequirements(projectId: Int): List<Pair<String, Int>> = runBlocking {
        val result = DatabaseSteps.query<Int, List<Pair<String, Int>>>(
            sql = SafeSQL.select(
                "SELECT item_id, required FROM resource_gathering WHERE project_id = ? ORDER BY item_id"
            ),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs ->
                val rows = mutableListOf<Pair<String, Int>>()
                while (rs.next()) rows.add(rs.getString("item_id") to rs.getInt("required"))
                rows
            }
        ).process(projectId)
        (result as Result.Success).value
    }
}
