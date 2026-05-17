package app.mcorg.presentation.handler

import app.mcorg.presentation.templated.dsl.CONFIRM_DELETE_MODAL_ID
import app.mcorg.presentation.templated.dsl.pageShell
import app.mcorg.presentation.utils.respondHtml
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

/**
 * Validates that confirmDeleteModal() renders with all 9 element IDs expected by
 * confirmation-modal.js. Any drift in IDs silently breaks all delete flows in production,
 * so this test guards the JS contract after the port from the legacy common/modal location.
 */
class ConfirmDeleteModalIT {

    @Test
    fun `confirmDeleteModal renders all 9 element IDs required by confirmation-modal js`() {
        testApplication {
            routing {
                get("/test-modal") {
                    call.respondHtml(pageShell {})
                }
            }

            val response = client.get("/test-modal")
            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.bodyAsText()

            // The dialog itself
            assertContains(body, "id=\"$CONFIRM_DELETE_MODAL_ID\"",
                message = "dialog#confirm-delete-modal must be present")

            // Title
            assertContains(body, "id=\"$CONFIRM_DELETE_MODAL_ID-title\"",
                message = "#confirm-delete-modal-title must be present")

            // Description
            assertContains(body, "id=\"$CONFIRM_DELETE_MODAL_ID-description\"",
                message = "#confirm-delete-modal-description must be present")

            // Warning
            assertContains(body, "id=\"$CONFIRM_DELETE_MODAL_ID-warning\"",
                message = "#confirm-delete-modal-warning must be present")

            // Input container (initially hidden via is-hidden class)
            assertContains(body, "id=\"$CONFIRM_DELETE_MODAL_ID-input-container\"",
                message = "#confirm-delete-modal-input-container must be present")

            // Input label
            assertContains(body, "id=\"$CONFIRM_DELETE_MODAL_ID-input-label\"",
                message = "#confirm-delete-modal-input-label must be present")

            // Input field
            assertContains(body, "id=\"$CONFIRM_DELETE_MODAL_ID-input\"",
                message = "#confirm-delete-modal-input must be present")

            // Confirm button (disabled by default)
            assertContains(body, "id=\"$CONFIRM_DELETE_MODAL_ID-confirm-btn\"",
                message = "#confirm-delete-modal-confirm-btn must be present")

            // Validation error (initially hidden via is-hidden class)
            assertContains(body, "id=\"$CONFIRM_DELETE_MODAL_ID-validation-error\"",
                message = "#confirm-delete-modal-validation-error must be present")

            // Verify is-hidden class is used instead of inline style display:none
            assertContains(body, "is-hidden",
                message = "is-hidden class must be used instead of inline style display:none")
        }
    }
}
