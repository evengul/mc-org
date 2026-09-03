package app.mcorg.presentation.handler

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.items.handleSearchItems
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `/items/search` is the second MCO-499 glyph surface, and the wide one: every combo on every
 * page renders its options from this one loop.
 *
 * Two things are under test, and the second is the one that actually breaks pages. The first is
 * that a glyph is emitted at all. The second is that adding a child element to the option did not
 * cost the option its click contract — `data-item-id`, `data-item-name` and the inline
 * `onclick="selectSearchedItem(this)"` are what four separate handlers (plan-view.js,
 * farm-modal.js, the draft form's inline script, and the two capture-phase listeners in
 * resource-panel.js) read to resolve a selection.
 *
 * The Testcontainers DB carries no ingested Minecraft data, so this class seeds its own items.
 * The names are deliberately outlandish: the container is shared across IT classes with no
 * truncation between them, and a query like "wool" would pick up whatever another class inserted.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ItemSearchGlyphIT {

    private val version = "1.21.4"

    /** Matched by an `_wool$` rule and tinted cyan; the tint is the point of the whole glyph set. */
    private val woolId = "minecraft:glyphit_cyan_wool"

    /** Matched by no rule at all, so it must draw the crossed-box unmapped mark. */
    private val unmappedId = "minecraft:glyphit_no_rule_covers_this"

    @BeforeAll
    fun setup() {
        runBlocking {
            // minecraft_items.version is a FK onto minecraft_version (V2_14_1). Other IT classes
            // get the parent row for free by creating a world; this one never creates one.
            version()
            item(woolId, "Glyphit Cyan Wool")
            item(unmappedId, "Glyphit Unmappable Thing")
        }
    }

    @Test
    fun `a search option carries an inline glyph svg`() = testApplication {
        setupRoutes()

        val response = client.get("/items/search?q=glyphit+cyan&versionRangeType=unbounded")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "<svg class=\"item-glyph")
        assertContains(body, "item-glyph--cyan")
        assertContains(body, "width=\"16px\" height=\"16px\"")
    }

    /**
     * The regression that would silently kill every combo on the site: a glyph is drawn, the page
     * looks right, and nothing selects any more.
     */
    @Test
    fun `the option keeps the attributes every selection handler reads`() = testApplication {
        setupRoutes()

        val response = client.get("/items/search?q=glyphit+cyan&versionRangeType=unbounded")
        val body = response.bodyAsText()

        assertContains(body, "class=\"item-search-option\"")
        assertContains(body, "data-item-id=\"$woolId\"")
        assertContains(body, "data-item-name=\"Glyphit Cyan Wool\"")
        assertContains(body, "onclick=\"selectSearchedItem(this)\"")
        // The name is still readable text inside the option, not replaced by the icon.
        assertContains(body, "Glyphit Cyan Wool")
    }

    /**
     * MCO-475's third leg — "make the render fallback visibly a gap" — only holds if the fallback
     * reaches a page. This is where it does, at the size a real option uses.
     */
    @Test
    fun `an item no rule covers renders the unmapped mark, not a plausible icon`() = testApplication {
        setupRoutes()

        val response = client.get("/items/search?q=glyphit+unmappable&versionRangeType=unbounded")
        val body = response.bodyAsText()

        assertContains(body, "item-glyph--unmapped")
        assertContains(body, "aria-label=\"glyphit_no_rule_covers_this\"")
    }

    @Test
    fun `an empty query still returns nothing`() = testApplication {
        setupRoutes()

        val response = client.get("/items/search?q=&versionRangeType=unbounded")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().isBlank())
    }

    @Test
    fun `a query matching nothing renders the empty state, with no glyph`() = testApplication {
        setupRoutes()

        val response = client.get("/items/search?q=glyphit+nothing+matches&versionRangeType=unbounded")
        val body = response.bodyAsText()

        assertContains(body, "item-search-empty")
        assertTrue(!body.contains("item-glyph"), "the empty state should not draw a glyph")
    }

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            route("/items") {
                get("/search") { call.handleSearchItems() }
            }
        }
    }

    private suspend fun version() {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO minecraft_version (version) VALUES (?) ON CONFLICT (version) DO NOTHING"
            ),
            parameterSetter = { stmt, _ -> stmt.setString(1, version) }
        ).process(Unit)
    }

    /**
     * Checked, not fire-and-forget. The first version of this class swallowed a foreign-key
     * violation here and every test failed on "No items found" — a message about the assertion,
     * not about the seed. A failed fixture should say so.
     */
    private suspend fun item(itemId: String, name: String) {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO minecraft_items (version, item_id, item_name) VALUES (?, ?, ?) " +
                    "ON CONFLICT (version, item_id) DO NOTHING"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, version)
                stmt.setString(2, itemId)
                stmt.setString(3, name)
            }
        ).process(Unit)
        if (result is Result.Failure) fail("could not seed $itemId: ${result.error}")
    }
}
