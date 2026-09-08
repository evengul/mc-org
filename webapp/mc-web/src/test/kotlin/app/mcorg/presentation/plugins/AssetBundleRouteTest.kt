package app.mcorg.presentation.plugins

import app.mcorg.presentation.templated.dsl.ScriptBundle
import app.mcorg.presentation.templated.dsl.StylesheetBundle
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * The bundle routes answer the URLs `pageShell` emits, and `styleguide.html`'s hash-less one.
 * Same harness as [app.mcorg.presentation.handler.ConfirmDeleteModalTest]: `testApplication`
 * boots the app module, which needs the database extension even for routes that never touch it.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class AssetBundleRouteTest {

    @Test
    fun `serves the stylesheet bundle at the URL pageShell links`() = testApplication {
        routing { assetBundles() }

        val response = client.get(StylesheetBundle.href())

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.CSS.withCharset(Charsets.UTF_8), response.contentType())
        val css = response.bodyAsText()
        assertContains(css, "/* ==== reset.css ==== */")
        assertContains(css, "/* ==== components/btn.css ==== */")
        assertContains(css, "/* ==== pages/worlds.css ==== */")
    }

    @Test
    fun `serves the script bundle at the URL pageShell links`() = testApplication {
        routing { assetBundles() }

        val response = client.get(ScriptBundle.href())

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.JavaScript.withCharset(Charsets.UTF_8), response.contentType())
        val js = response.bodyAsText()
        assertContains(js, "/* ==== confirmation-modal.js ==== */")
        assertContains(js, "window.selectSearchedItem = function")
        assertContains(js, "/* ==== worlds.js ==== */")
    }

    @Test
    fun `serves the current bundle for any version, so a static page can link it without the hash`() = testApplication {
        routing { assetBundles() }

        val response = client.get("/static/seam.latest.css")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(StylesheetBundle.current().content, response.bodyAsText())
    }

    @Test
    fun `a file by any other name is not a bundle`() = testApplication {
        routing { assetBundles() }

        assertEquals(HttpStatusCode.NotFound, client.get("/static/seam.css").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/static/seam.js").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/static/styles/reset.css").status)
    }
}
