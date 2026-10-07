package app.mcorg.presentation.handler

import app.mcorg.pipeline.failure.AppFailure
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.server.routing.post
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * MCO-590 — a [FailureResponse.RedirectTo] to the sign-in page, reached through
 * `defaultHandleError`. Under HTMX the page to come back to is the one the user clicked on, not the
 * fragment endpoint the click posted to; `AuthPluginIT` pins the same rule for the auth plugin.
 */
class RedirectFailureTest {

    @Test
    fun `MissingToken under HTMX sends the page back through sign-in, not the fragment`() = testApplication {
        routing { post("/fragment") { call.defaultHandleError(AppFailure.AuthError.MissingToken) } }

        val response = createClient { followRedirects = false }.post("/fragment") {
            header("HX-Request", "true")
            header("HX-Current-URL", "http://localhost/worlds/3?tab=x")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(response.headers["Location"])
        assertEquals("/worlds/3?tab=x", Url(response.headers["HX-Redirect"]!!).parameters["redirect_to"])
    }

    @Test
    fun `MissingToken on a page load sends the request's own URI back through sign-in`() = testApplication {
        routing { post("/page") { call.defaultHandleError(AppFailure.AuthError.MissingToken) } }

        val response = createClient { followRedirects = false }.post("/page?tab=x") {
            header("HX-Current-URL", "http://localhost/elsewhere")
        }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/page?tab=x", Url(response.headers["Location"]!!).parameters["redirect_to"])
    }
}
