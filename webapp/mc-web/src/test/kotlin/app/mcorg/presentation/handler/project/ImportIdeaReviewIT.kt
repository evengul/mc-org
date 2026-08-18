package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.Role
import app.mcorg.config.CacheManager
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.ReviewedMaterial
import app.mcorg.pipeline.project.ReviewedMaterialsCodec
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
    private var airyIdeaId: Int = 0
    private var expensiveIdeaId: Int = 0
    private var wideIdeaId: Int = 0
    private var multiModeIdeaId: Int = 0

    @BeforeAll
    fun setup() {
        worldId = createWorld("Idea Import IT World")
        seedItem("minecraft:iron_ingot", "Iron Ingot")
        seedItem("minecraft:oak_planks", "Oak Planks")
        seedItem("minecraft:redstone", "Redstone Dust")
        seedItem("minecraft:air", "Air (Block)")
        seedItem("minecraft:water", "Water")
        seedItem("minecraft:water_bucket", "Water Bucket")
        ideaId = createIdea("Iron Farm Idea")
        addRequirement(ideaId, "minecraft:iron_ingot", 64)
        addRequirement(ideaId, "minecraft:oak_planks", 32)
        addRequirement(ideaId, "minecraft:redstone", 8)

        // The MCO-305 case, from a real idea: a schematic-derived idea whose largest row was
        // nine million air blocks — 90% of the build's material total.
        airyIdeaId = createIdea("Ghast Farm Roof")
        addRequirement(airyIdeaId, "minecraft:air", 9_389_854)
        addRequirement(airyIdeaId, "minecraft:water", 12)
        addRequirement(airyIdeaId, "minecraft:oak_planks", 64)

        // MCO-397: a curated "slow to gather" row, which must stay off the strip.
        seedItem("minecraft:elytra", "Elytra")
        expensiveIdeaId = createIdea("Elytra Wall")
        addRequirement(expensiveIdeaId, "minecraft:elytra", 3)
        addRequirement(expensiveIdeaId, "minecraft:oak_planks", 20)

        // MCO-315: an idea whose list is far wider than the ~466 rows that used to fit in a
        // request body. The idea door shares the review screen, so it shared the data loss.
        wideIdeaId = createIdea("Very Wide Idea")
        seedItems((1..BULK_ROWS).map { "minecraft:bulk_item_$it" to "Bulk Item $it" })
        addRequirements(wideIdeaId, (1..BULK_ROWS).map { "minecraft:bulk_item_$it" to it })

        // MCO-411: what the idea produces has to survive the import, or the farm the user just
        // imported supplies nothing and the roadmap draws no edge from it (MCO-287's model).
        addProductionMode(ideaId, "Default", listOf("minecraft:iron_ingot" to 620))

        // Two modes, no choice recorded anywhere yet: the import takes the highest-yield one
        // (ratesForImport). An unmeasured rate arrives as project_productions' own 0.
        seedItem("minecraft:bone", "Bone")
        seedItem("minecraft:blaze_rod", "Blaze Rod")
        seedItem("minecraft:blaze_powder", "Blaze Powder")
        multiModeIdeaId = createIdea("Fortress Farm")
        addRequirement(multiModeIdeaId, "minecraft:oak_planks", 10)
        addProductionMode(multiModeIdeaId, "Skeletons only", listOf("minecraft:bone" to 700))
        addProductionMode(
            multiModeIdeaId,
            "Everything on",
            listOf("minecraft:bone" to 500, "minecraft:blaze_rod" to 400, "minecraft:blaze_powder" to null),
        )
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
        // One field for the whole list since MCO-315, not one parameter per row.
        assertContains(body, """name="materials" value="v1;3;""")
        assertContains(body, "minecraft:iron_ingot=64")
        assertContains(body, "minecraft:oak_planks=32")
        assertFalse(body.contains("qty["), "the per-row parameters are gone")
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

    // ---- warn early (MCO-305) ------------------------------------------------------

    @Test
    fun `air never reaches the review screen`() = testApplication {
        setupRoutes()

        val response = client.get("/ideas/$airyIdeaId/import/review?worldId=$worldId") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertFalse(body.contains("minecraft:air"), "air is filtered, not offered as a row")
        assertFalse(body.contains("9389854"), "and its quantity goes with it")
        assertContains(body, "minecraft:oak_planks=64", message = "the real materials still arrive")
    }

    @Test
    fun `a fluid arrives as the bucket you carry, with the cell count as context`() = testApplication {
        // MCO-396. This used to assert the opposite — water stayed as a row and was flagged
        // "Not really materials". That reading is what put `Blocked: Water — no feasible
        // source found` in the plan, because nothing produces water: it is not an item.
        setupRoutes()

        val response = client.get("/ideas/$airyIdeaId/import/review?worldId=$worldId") { addAuthCookie(this) }

        val body = response.bodyAsText()
        assertContains(body, "minecraft:water_bucket=1", message = "one bucket, however many cells")
        assertFalse(body.contains("minecraft:water="), "the fluid id itself must not be a row")
        assertContains(body, "placed 12×", message = "the schematic's own number stays visible")
        assertFalse(body.contains("Not really materials"), "the warning kind is gone entirely")
    }

    @Test
    fun `a slow-to-gather row keeps its chip but never reaches the strip`() = testApplication {
        // MCO-397. An elytra is a real expedition and the row says so, but the user picked the
        // build knowing that — it is not worth a "!" above the fold. The strip is reserved for
        // creative-only rows, the one kind that asks for a decision before the import lands.
        setupRoutes()

        val response = client.get("/ideas/$expensiveIdeaId/import/review?worldId=$worldId") {
            addAuthCookie(this)
        }

        val body = response.bodyAsText()
        assertContains(body, "Slow to gather", message = "the row chip stays")
        assertFalse(body.contains("Expensive to gather"), "but the strip heading is gone")
        assertFalse(body.contains("callout__icon"), "and with nothing left to say, so is the whole strip")
    }

    @Test
    fun `an import that includes air drops it instead of creating a resource row`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        // The review screen never renders an air row, so this is a hand-rolled post — the
        // point being that "air is never a material" holds where the list becomes final too.
        val response = client.post("/ideas/$airyIdeaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "worldId=$worldId&name=Roof&" + materials(
                    "minecraft:air" to 9389854,
                    "minecraft:oak_planks" to 64,
                )
            )
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val projectId = response.headers["Location"]!!.substringAfterLast("/").toInt()

        assertEquals(listOf("minecraft:oak_planks" to 64), readRequirements(projectId))
    }

    // ---- create ------------------------------------------------------------------

    @Test
    fun `import creates the project from the reviewed rows only`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        val response = client.post("/ideas/$ideaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "worldId=$worldId&name=Reviewed Iron Farm&" + materials(
                    "minecraft:iron_ingot" to 64,
                    "!minecraft:oak_planks" to 32,
                    "minecraft:redstone" to 8,
                )
            )
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
    fun `import records what the idea produces`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        val response = client.post("/ideas/$ideaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("worldId=$worldId&" + materials("minecraft:iron_ingot" to 64))
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val projectId = response.headers["Location"]!!.substringAfterLast("/").toInt()

        // The whole of MCO-411: this list was empty for every import ever made, so no imported
        // farm has ever supplied anything.
        assertEquals(listOf("minecraft:iron_ingot" to 620), readProductions(projectId))
    }

    @Test
    fun `an idea with several modes imports the highest-yield one`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        val response = client.post("/ideas/$multiModeIdeaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("worldId=$worldId&" + materials("minecraft:oak_planks" to 10))
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val projectId = response.headers["Location"]!!.substringAfterLast("/").toInt()

        assertEquals(
            listOf(
                "minecraft:blaze_powder" to 0,
                "minecraft:blaze_rod" to 400,
                "minecraft:bone" to 500,
            ),
            readProductions(projectId),
            "900/h across three items beats the skeletons-only mode's 700",
        )
    }

    @Test
    fun `a submission with no rows is refused rather than importing the whole idea`() = testApplication {
        setupRoutes()
        val before = countProjects()

        // Unchecking every row sends a list where every row is struck. Importing the idea's
        // full list here would do the opposite of what the user just asked for.
        val response = client.post("/ideas/$ideaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("worldId=$worldId&" + materials("!minecraft:iron_ingot" to 64))
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(before, countProjects())
    }

    @Test
    fun `a submission with no material list at all is refused`() = testApplication {
        setupRoutes()
        val before = countProjects()

        // Also the shape a review page rendered before MCO-315 would send. There is no
        // "import the whole idea instead" fallback, by design.
        val response = client.post("/ideas/$ideaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("worldId=$worldId&qty[minecraft:iron_ingot]=64")
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
            setBody("worldId=$worldId&" + materials("minecraft:not_a_real_item" to 1))
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
            setBody("worldId=$worldId&" + materials("minecraft:iron_ingot" to 1))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(before, countProjects())
    }

    // ---- MCO-315 — a list wider than the old request-parameter cap -------------------

    @Test
    fun `an idea list far past the old 466-row cutoff imports every included row`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        val struck = (1..BULK_ROWS).filter { it % 7 == 0 }.map { "minecraft:bulk_item_$it" }.toSet()
        val rows = (1..BULK_ROWS).map { "minecraft:bulk_item_$it" to it }

        val response = client.post("/ideas/$wideIdeaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "worldId=$worldId&name=Wide Import&" + materials(
                    *rows.map { (id, amount) -> (if (id in struck) "!$id" else id) to amount }.toTypedArray()
                )
            )
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val projectId = response.headers["Location"]!!.substringAfterLast("/").toInt()

        val persisted = readRequirements(projectId).toMap()
        assertEquals(BULK_ROWS - struck.size, persisted.size, "every included row is persisted")
        rows.forEach { (id, amount) ->
            if (id in struck) {
                assertFalse(id in persisted, "$id was struck and must not be persisted")
            } else {
                assertEquals(amount, persisted[id], "$id must survive the import")
            }
        }
    }

    @Test
    fun `an idea list that arrives short is refused rather than partly imported`() = testApplication {
        setupRoutes()
        val before = countProjects()

        val full = ReviewedMaterialsCodec.encode(
            (1..BULK_ROWS).map { ReviewedMaterial("minecraft:bulk_item_$it", it, included = true) }
        )
        val truncated = full.split(";").take(2 + 466).joinToString(";")

        val response = client.post("/ideas/$wideIdeaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("worldId=$worldId&name=Cut Short&${ReviewedMaterialsCodec.FIELD}=$truncated")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertContains(response.bodyAsText(), "466 of $BULK_ROWS")
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

    private fun addRequirements(ideaId: Int, requirements: List<Pair<String, Int>>) = runBlocking {
        DatabaseSteps.batchUpdate<Pair<String, Int>>(
            SafeSQL.insert("INSERT INTO idea_item_requirements (idea_id, item_id, quantity) VALUES (?, ?, ?)"),
            parameterSetter = { stmt, (itemId, quantity) ->
                stmt.setInt(1, ideaId)
                stmt.setString(2, itemId)
                stmt.setInt(3, quantity)
            }
        ).process(requirements)
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

    private fun addProductionMode(ideaId: Int, name: String, rates: List<Pair<String, Int?>>) = runBlocking {
        val modeId = DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO idea_production_modes (idea_id, name, position) " +
                    "VALUES (?, ?, (SELECT COALESCE(MAX(position) + 1, 0) FROM idea_production_modes WHERE idea_id = ?)) " +
                    "RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, ideaId)
                stmt.setString(2, name)
                stmt.setInt(3, ideaId)
            }
        ).process(Unit).let { (it as Result.Success).value }

        DatabaseSteps.batchUpdate<Pair<String, Int?>>(
            SafeSQL.insert("INSERT INTO idea_production_rates (mode_id, item_id, rate_per_hour) VALUES (?, ?, ?)"),
            parameterSetter = { stmt, (itemId, rate) ->
                stmt.setInt(1, modeId)
                stmt.setString(2, itemId)
                if (rate == null) stmt.setNull(3, java.sql.Types.INTEGER) else stmt.setInt(3, rate)
            }
        ).process(rates)
    }

    private fun readProductions(projectId: Int): List<Pair<String, Int>> = runBlocking {
        val result = DatabaseSteps.query<Int, List<Pair<String, Int>>>(
            sql = SafeSQL.select(
                "SELECT item_id, rate_per_hour FROM project_productions WHERE project_id = ? ORDER BY item_id"
            ),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs ->
                val rows = mutableListOf<Pair<String, Int>>()
                while (rs.next()) rows.add(rs.getString("item_id") to rs.getInt("rate_per_hour"))
                rows
            }
        ).process(projectId)
        (result as Result.Success).value
    }

    /**
     * A request body carrying the whole list in one field, as the review page now sends it.
     * Prefix an id with `!` to mark the row struck.
     */
    private fun materials(vararg rows: Pair<String, Int>): String {
        val encoded = ReviewedMaterialsCodec.encode(
            rows.map { (id, amount) ->
                ReviewedMaterial(id.removePrefix("!"), amount, included = !id.startsWith("!"))
            }
        )
        return "${ReviewedMaterialsCodec.FIELD}=$encoded"
    }

    private companion object {
        /** Comfortably past the ~466 rows that used to fit in a request body. */
        const val BULK_ROWS = 600
    }
}
