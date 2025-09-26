package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.ParametersBuilder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldInputValidatorTest {
    @Test
    fun `Should return success when all fields are OK`() {
        val input = createInput()

        val result = runBlocking { WorldInputValidator.validateWorldInput(input) }

        assertTrue(result is Result.Success)
        val data = result.value
        assertEquals("My World", data.name)
        assertEquals("A cool world", data.description)
        assertEquals("1.16.5", data.version.toString())
    }

    @Test
    fun `Should return failure when name is missing`() {
        val input = createInput(name = null)

        val result = runBlocking { WorldInputValidator.validateWorldInput(input) }
        assertTrue(result is Result.Failure)
        assertEquals(result.error.size, 1)
        val error = result.error[0]
        assertTrue(error is ValidationFailure.MissingParameter)
        assertEquals("name", error.parameterName)
    }

    @Test
    fun `Should return failure when version is invalid`() {
        val input = createInput(version = "invalid_version")

        val result = runBlocking { WorldInputValidator.validateWorldInput(input) }
        assertTrue(result is Result.Failure)
        assertEquals(result.error.size, 1)
        val error = result.error[0]
        assertTrue(error is ValidationFailure.CustomValidation)
        assertEquals("version", error.parameterName)
        assertContains(error.message, "Invalid Minecraft version")
    }

    @Test
    fun `Should return success when description is missing`() {
        val input = createInput(description = null)
        val result = runBlocking { WorldInputValidator.validateWorldInput(input) }

        assertTrue(result is Result.Success)
        val data = result.value
        assertEquals("", data.description)
    }

    @Test
    fun `Should return failure when both name and version are invalid`() {
        val input = createInput(name = null, version = "invalid_version")
        val result = runBlocking { WorldInputValidator.validateWorldInput(input) }

        assertTrue(result is Result.Failure)
        assertEquals(2, result.error.size)
        val nameError = result.error.find { it is ValidationFailure.MissingParameter } as ValidationFailure.MissingParameter
        val versionError = result.error.find { it is ValidationFailure.CustomValidation } as ValidationFailure.CustomValidation
        assertEquals("name", nameError.parameterName)
        assertEquals("version", versionError.parameterName)
    }

    private fun createInput(
        name: String? = "My World",
        description: String? = "A cool world",
        version: String? = "1.16.5"
    ) = ParametersBuilder().apply {
        name?.let { append("name", it) }
        description?.let { append("description", it) }
        version?.let { append("version", it) }
    }.build()
}