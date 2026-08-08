package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.handleSubstituteInImportReview
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldAdminPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * MCO-304 — batch substitution on the import review screen, end to end: the form goes out,
 * a rewritten materials section comes back, and nothing is written to the database.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ImportReviewSubstitutionIT : WithUser() {

    private var worldId: Int = 0

    private val woods = listOf("oak", "spruce", "birch", "jungle", "acacia")

    @BeforeAll
    fun setup() {
        worldId = createWorld()
        // A catalog wide enough for the vocabulary to recognise a family — the tokens are
        // derived from the catalog's own shape, so a two-item catalog would find nothing.
        woods.forEach { wood ->
            seedItem("minecraft:${wood}_planks", "${wood.replaceFirstChar { it.uppercase() }} Planks")
            seedItem("minecraft:${wood}_slab", "${wood.replaceFirstChar { it.uppercase() }} Slab")
            seedItem("minecraft:${wood}_stairs", "${wood.replaceFirstChar { it.uppercase() }} Stairs")
        }
        seedItem("minecraft:stone", "Stone")
    }

    @Test
    fun `swapping a family rewrites every row and leaves the rest alone`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/import-review/substitute") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "from=oak&to[oak]=spruce" +
                    "&row[minecraft:oak_planks]=64&qty[minecraft:oak_planks]=64" +
                    "&row[minecraft:oak_stairs]=12&qty[minecraft:oak_stairs]=12" +
                    "&row[minecraft:stone]=100&qty[minecraft:stone]=100"
            )
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val body = response.bodyAsText()
        assertContains(body, "row[minecraft:spruce_planks]")
        assertContains(body, "row[minecraft:spruce_stairs]")
        assertContains(body, "row[minecraft:stone]", message = "an unrelated row is untouched")
        assertFalse(body.contains("minecraft:oak_planks"), "no oak survives the swap")
        assertFalse(body.contains("minecraft:oak_stairs"), "no oak survives the swap")
    }

    @Test
    fun `an excluded row survives the swap and stays excluded`() = testApplication {
        setupRoutes()

        // oak_stairs is struck — it has a `row[...]` but no `qty[...]`, exactly as the form
        // sends an unchecked box. It must come back as spruce stairs, still unchecked.
        val response = client.post("/worlds/$worldId/projects/import-review/substitute") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "from=oak&to[oak]=spruce" +
                    "&row[minecraft:oak_planks]=64&qty[minecraft:oak_planks]=64" +
                    "&row[minecraft:oak_stairs]=12"
            )
        }

        val body = response.bodyAsText()
        assertContains(body, "row[minecraft:spruce_stairs]", message = "the struck row is not lost")
        assertTrue(isChecked(body, "minecraft:spruce_planks"), "the kept row is still kept")
        assertFalse(isChecked(body, "minecraft:spruce_stairs"), "the struck row is still struck")
    }

    @Test
    fun `the swap control comes back describing the list's new state`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/import-review/substitute") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "from=oak&to[oak]=spruce" +
                    "&row[minecraft:oak_planks]=64&qty[minecraft:oak_planks]=64" +
                    "&row[minecraft:oak_slab]=8&qty[minecraft:oak_slab]=8"
            )
        }

        val body = response.bodyAsText()
        assertContains(body, "import-review-materials", message = "the fragment replaces itself")
        assertContains(body, "All Spruce (2 rows)", message = "the family is now spruce")
        assertContains(body, """name="to[spruce]"""")
    }

    @Test
    fun `swapping onto an id already in the list merges the two rows`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/import-review/substitute") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "from=oak&to[oak]=spruce" +
                    "&row[minecraft:oak_planks]=64&qty[minecraft:oak_planks]=64" +
                    "&row[minecraft:spruce_planks]=10&qty[minecraft:spruce_planks]=10" +
                    "&row[minecraft:oak_slab]=1&qty[minecraft:oak_slab]=1"
            )
        }

        val body = response.bodyAsText()
        assertContains(body, """name="row[minecraft:spruce_planks]" value="74"""")
    }

    @Test
    fun `an unknown item id is refused`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/import-review/substitute") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("from=oak&to[oak]=spruce&row[minecraft:not_a_real_item]=1")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `a swap with no target is refused`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/import-review/substitute") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("from=oak&row[minecraft:oak_planks]=64&qty[minecraft:oak_planks]=64")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `a non-member cannot substitute in someone else's world`() = testApplication {
        setupRoutes()
        val outsider = createExtraUser("substitute-outsider")

        val response = client.post("/worlds/$worldId/projects/import-review/substitute") {
            addAuthCookie(this, outsider)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("from=oak&to[oak]=spruce&row[minecraft:oak_planks]=64")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    /** Reads the rendered include-checkbox for one row back out of the fragment. */
    private fun isChecked(body: String, itemId: String): Boolean {
        val rowId = itemId.replace(Regex("[^a-zA-Z0-9]"), "-")
        val tag = Regex("""<input[^>]*id="include-$rowId"[^>]*>""").find(body)
        assertNotNull(tag, "no include checkbox rendered for $itemId")
        return "checked" in tag.value
    }

    // ---- routing — mirrors WorldHandler ---------------------------------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    route("/import-review/substitute") {
                        install(WorldAdminPlugin)
                        post { call.handleSubstituteInImportReview() }
                    }
                }
            }
        }
    }

    // ---- fixtures --------------------------------------------------------------------

    private fun createWorld(): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = "Substitution IT World",
                description = "test",
                version = MinecraftVersion.fromString("1.21.4"),
            )
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
}
