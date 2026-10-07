package app.mcorg.presentation.templated.dsl

import app.mcorg.pipeline.failure.ValidationFailure
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FieldErrorTest {

    @Test
    fun `a slot is keyed by the parameter name, with no id`() {
        val html = createHTML().div { fieldError("tags[]") }

        assertContains(html, "data-error-for=\"tags\"")
        assertFalse(html.contains(" id="), "two forms on one page may share a field name: $html")
    }

    @Test
    fun `each failed field is a partial aimed at its slot inside the form that sent it`() {
        val html = fieldMessages(
            listOf(
                ValidationFailure.InvalidLength("name", 3, 100),
                ValidationFailure.CustomValidation("productions", "Add at least one produced item"),
            )
        )

        assertEquals(3, Regex("<template hx=\"\" type=\"partial\"").findAll(html).count(), html)
        assertContains(html, "hx-target=\"find [data-error-for='name']\"")
        assertContains(html, "hx-target=\"find [data-error-for='productions']\"")
        assertContains(html, "Name must be between 3 and 100 characters.")
    }

    @Test
    fun `every message also travels in the fallback alert, for a field with no slot`() {
        val html = fieldMessages(listOf(ValidationFailure.MissingParameter("farmType")))
        val alert = html.substringAfter("hx-target=\"#$ALERT_CONTAINER_ID\"")

        assertContains(alert, FIELD_FALLBACK)
        assertContains(alert, "$FIELD_MESSAGE=\"farmType\"")
        assertContains(alert, "Farm type is required.")
    }

    @Test
    fun `nothing is left over for the main swap`() {
        // A response of nothing but partials leaves the main target alone in htmx 4; a stray
        // element would be swapped into it.
        val html = fieldMessages(listOf(ValidationFailure.MissingParameter("name")))

        assertTrue(html.replace(Regex("<template[\\s\\S]*?</template>"), "").isBlank(), html)
    }

    @Test
    fun `a quote in a field name cannot end the selector`() {
        val html = fieldMessages(listOf(ValidationFailure.MissingParameter("a'b")))

        assertContains(html, "[data-error-for='a\\'b']")
    }
}
