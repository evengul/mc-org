package app.mcorg.pipeline.failure

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ValidationFailureMessagesTest {

    @Test
    fun `a parameter name reads as words, first one capitalised`() {
        assertEquals("Farm name", fieldLabel("farmName"))
        assertEquals("Channel id", fieldLabel("channel_id"))
        assertEquals("Size › width", fieldLabel("categoryData.size.width"))
        assertEquals("Tags", fieldLabel("tags[]"))
        assertEquals("Team members › role", fieldLabel("teamMembers[0][role]"))
    }

    @Test
    fun `each failure says what is wrong with the field by its label`() {
        assertEquals("Name must be between 3 and 100 characters.", ValidationFailure.InvalidLength("name", 3, 100).userMessage())
        assertEquals("To username is required.", ValidationFailure.MissingParameter("toUsername").userMessage())
        assertEquals("Farm scale threshold must be at least 1.", ValidationFailure.OutOfRange("farmScaleThreshold", min = 1).userMessage())
        assertEquals("Type must be one of: A, B.", ValidationFailure.InvalidValue("type", listOf("A", "B")).userMessage())
    }

    @Test
    fun `a custom message is shown as written`() {
        assertEquals("Add at least one produced item", ValidationFailure.CustomValidation("productions", "Add at least one produced item").userMessage())
    }
}
