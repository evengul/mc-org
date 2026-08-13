package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.ReviewedMaterial
import app.mcorg.pipeline.project.ReviewedMaterialsCodec
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
        // MCO-315: enough rows to be past the ~466 that used to fit in a request body.
        seedItems((1..BULK_ROWS).map { "minecraft:bulk_item_$it" to "Bulk Item $it" })
    }

    @Test
    fun `swapping a family rewrites every row and leaves the rest alone`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/import-review/substitute") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                swap(
                    "oak", "spruce",
                    "minecraft:oak_planks" to 64,
                    "minecraft:oak_stairs" to 12,
                    "minecraft:stone" to 100,
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val body = response.bodyAsText()
        assertContains(body, "minecraft:spruce_planks=64")
        assertContains(body, "minecraft:spruce_stairs=12")
        assertContains(body, "minecraft:stone=100", message = "an unrelated row is untouched")
        assertFalse(body.contains("minecraft:oak_planks"), "no oak survives the swap")
        assertFalse(body.contains("minecraft:oak_stairs"), "no oak survives the swap")
    }

    @Test
    fun `an excluded row survives the swap and stays excluded`() = testApplication {
        setupRoutes()

        // oak_stairs is struck — it rides along in the list marked excluded, exactly as the
        // form sends an unchecked box. It must come back as spruce stairs, still unchecked.
        val response = client.post("/worlds/$worldId/projects/import-review/substitute") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                swap("oak", "spruce", "minecraft:oak_planks" to 64, "!minecraft:oak_stairs" to 12)
            )
        }

        val body = response.bodyAsText()
        assertContains(body, "!minecraft:spruce_stairs=12", message = "the struck row is not lost")
        assertTrue(isChecked(body, "minecraft:spruce_planks"), "the kept row is still kept")
        assertFalse(isChecked(body, "minecraft:spruce_stairs"), "the struck row is still struck")
    }

    @Test
    fun `the swap control comes back describing the list's new state`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/import-review/substitute") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(swap("oak", "spruce", "minecraft:oak_planks" to 64, "minecraft:oak_slab" to 8))
        }

        val body = response.bodyAsText()
        assertContains(body, "import-review-materials", message = "the fragment replaces itself")
        assertContains(body, "All Spruce (2 rows)", message = "the family is now spruce")
        assertContains(body, """id="swap-to-spruce"""")
        // The selects carry no name (MCO-315) — a create submit must not drag one parameter
        // per family along with it.
        assertFalse(body.contains("""name="to["""), "the swap selects are not form controls")
    }

    @Test
    fun `swapping onto an id already in the list merges the two rows`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/import-review/substitute") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                swap(
                    "oak", "spruce",
                    "minecraft:oak_planks" to 64,
                    "minecraft:spruce_planks" to 10,
                    "minecraft:oak_slab" to 1,
                )
            )
        }

        assertContains(response.bodyAsText(), "minecraft:spruce_planks=74")
    }

    @Test
    fun `an unknown item id is refused`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/import-review/substitute") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(swap("oak", "spruce", "minecraft:not_a_real_item" to 1))
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `a swap with no target is refused`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/import-review/substitute") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("from=oak&" + materials("minecraft:oak_planks" to 64))
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
            setBody(swap("oak", "spruce", "minecraft:oak_planks" to 64))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // ---- MCO-315 — a swap on a list wider than the old parameter cap ------------------

    @Test
    fun `a swap on a list far past the old 466-row cutoff keeps every row`() = testApplication {
        setupRoutes()

        // Two parameters per row meant the substitute POST truncated too: a family swap on a
        // large review silently deleted the tail of the list, exclusions and all.
        val struck = (1..BULK_ROWS).filter { it % 5 == 0 }.map { "minecraft:bulk_item_$it" }.toSet()
        val rows = listOf("minecraft:oak_planks" to 64, "!minecraft:oak_stairs" to 12) +
            (1..BULK_ROWS).map { (if ("minecraft:bulk_item_$it" in struck) "!" else "") + "minecraft:bulk_item_$it" to it }

        val response = client.post("/worlds/$worldId/projects/import-review/substitute") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(swap("oak", "spruce", *rows.toTypedArray()))
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val body = response.bodyAsText()

        assertContains(body, "${BULK_ROWS + 2} items", message = "not one row is dropped on the way in")
        assertContains(body, "minecraft:spruce_planks=64")
        assertContains(body, "!minecraft:spruce_stairs=12", message = "the struck row is still struck")
        // The rows that used to fall off the end — the last one especially.
        assertContains(body, "minecraft:bulk_item_$BULK_ROWS=$BULK_ROWS")
        struck.forEach { assertContains(body, "!$it=", message = "$it must come back struck") }
    }

    @Test
    fun `a swap on a list that arrives short is refused rather than applied in part`() = testApplication {
        setupRoutes()

        val full = ReviewedMaterialsCodec.encode(
            (1..BULK_ROWS).map { ReviewedMaterial("minecraft:bulk_item_$it", it, included = true) }
        )
        val truncated = full.split(";").take(2 + 466).joinToString(";")

        val response = client.post("/worlds/$worldId/projects/import-review/substitute") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("from=oak&to=spruce&${ReviewedMaterialsCodec.FIELD}=$truncated")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertContains(response.bodyAsText(), "466 of $BULK_ROWS")
    }

    /**
     * The whole list in one field, as the review page now sends it (MCO-315). Prefix an id
     * with `!` to mark the row struck.
     */
    private fun materials(vararg rows: Pair<String, Int>): String {
        val encoded = ReviewedMaterialsCodec.encode(
            rows.map { (id, amount) ->
                ReviewedMaterial(id.removePrefix("!"), amount, included = !id.startsWith("!"))
            }
        )
        return "${ReviewedMaterialsCodec.FIELD}=$encoded"
    }

    private fun swap(from: String, to: String, vararg rows: Pair<String, Int>): String =
        "from=$from&to=$to&" + materials(*rows)

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

    private companion object {
        /** Comfortably past the ~466 rows that used to fit in a request body. */
        const val BULK_ROWS = 600
    }
}
