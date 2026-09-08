package app.mcorg.presentation.plugins

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
 * The bundle route answers the URL `pageShell` emits, and `styleguide.html`'s hash-less one.
 * Same harness as [app.mcorg.presentation.handler.ConfirmDeleteModalTest]: `testApplication`
 * boots the app module, which needs the database extension even for a route that never touches it.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class StylesheetBundleRouteTest {

    @Test
    fun `serves the bundle at the URL pageShell links`() = testApplication {
        routing { stylesheetBundle() }

        val response = client.get(StylesheetBundle.href())

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.CSS.withCharset(Charsets.UTF_8), response.contentType())
        val css = response.bodyAsText()
        assertContains(css, "/* ==== reset.css ==== */")
        assertContains(css, "/* ==== components/btn.css ==== */")
        assertContains(css, "/* ==== pages/worlds.css ==== */")
    }

    @Test
    fun `serves the current bundle for any version, so a static page can link it without the hash`() = testApplication {
        routing { stylesheetBundle() }

        val response = client.get("/static/seam.latest.css")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(StylesheetBundle.current().css, response.bodyAsText())
    }

    @Test
    fun `a sheet by any other name is not the bundle`() = testApplication {
        routing { stylesheetBundle() }

        assertEquals(HttpStatusCode.NotFound, client.get("/static/seam.css").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/static/styles/reset.css").status)
    }
}
