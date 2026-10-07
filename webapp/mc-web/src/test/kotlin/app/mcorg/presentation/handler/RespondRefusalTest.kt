package app.mcorg.presentation.handler

import app.mcorg.pipeline.failure.AppFailure
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MCO-158 / MCO-436 — [respondRefusal]'s two shapes, reached the way most refusals reach it: a
 * pipeline failure through `defaultHandleError`.
 *
 * The route-plugin cases need a world and a membership row, so they are in `PluginRefusalIT`.
 */
class RespondRefusalTest {

    @Test
    fun `a pipeline's NotAuthorized renders the forbidden page on a page load`() = testApplication {
        failingWith(AppFailure.AuthError.NotAuthorized)

        val response = client.get("/fails")

        assertPage(response, HttpStatusCode.Forbidden, "403 — Forbidden")
        assertTrue(response.bodyAsText().contains("You do not have permission"), "should carry the reason")
    }

    @Test
    fun `a pipeline's NotAuthorized is still the alert under HTMX`() = testApplication {
        failingWith(AppFailure.AuthError.NotAuthorized)

        val response = client.get("/fails") { htmx() }

        assertAlert(response, HttpStatusCode.Forbidden, "not-authorized-error")
    }

    @Test
    fun `an unexpected failure renders the 500 page on a page load`() = testApplication {
        failingWith(AppFailure.DatabaseError.ConnectionError)

        val response = client.get("/fails")

        assertPage(response, HttpStatusCode.InternalServerError, "500 — Something Broke")
    }

    @Test
    fun `any other status gets a page named after it`() = testApplication {
        routing {
            get("/conflict") { call.respondRefusal(HttpStatusCode.Conflict, "Conflict", "Someone got there first.", "conflict-error") }
        }

        val response = client.get("/conflict")

        assertPage(response, HttpStatusCode.Conflict, "409 — Conflict")
        assertTrue(response.bodyAsText().contains("Someone got there first."), "should carry the reason")
    }

    private fun ApplicationTestBuilder.failingWith(failure: AppFailure) = routing {
        get("/fails") { call.defaultHandleError(failure) }
    }

    private fun HttpRequestBuilder.htmx() = header("HX-Request", "true")

    private suspend fun assertPage(response: HttpResponse, status: HttpStatusCode, heading: String) {
        val body = response.bodyAsText()
        assertEquals(status, response.status, "body was: ${body.take(300)}")
        assertTrue(body.contains(heading), "should render '$heading'; was: ${body.take(300)}")
        assertTrue(body.contains("error-page__card"), "should be the full status page with chrome")
        assertFalse(body.contains("hx-swap-oob"), "a page load has no use for an out-of-band swap")
    }

    private suspend fun assertAlert(response: HttpResponse, status: HttpStatusCode, alertId: String) {
        val body = response.bodyAsText()
        assertEquals(status, response.status, "body was: ${body.take(300)}")
        // htmx swaps no error into its target (noSwap, Layout.kt) and that overrides HX-Retarget,
        // so the alert has to arrive out of band.
        assertTrue(body.contains("hx-swap-oob=\"afterbegin:#alert-container\""), "should be swapped out of band; was: ${body.take(300)}")
        assertTrue(body.contains("id=\"$alertId\""), "should be the $alertId alert; was: ${body.take(300)}")
        assertFalse(body.contains("<html", ignoreCase = true), "a fragment, not a whole page")
    }
}
