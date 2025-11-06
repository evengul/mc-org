package app.mcorg.pipeline.world.settings

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.world.settings.general.UpdateWorldNameFailures
import app.mcorg.pipeline.world.settings.general.UpdateWorldNameInput
import app.mcorg.pipeline.world.settings.general.ValidateWorldNameInputStep
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UpdateWorldNameTest {

    private fun createParameters(vararg pairs: Pair<String, String>): Parameters {
        val builder = ParametersBuilder()
        pairs.forEach { (key, value) -> builder.append(key, value) }
        return builder.build()
    }

    @Test
    fun `ValidateWorldNameInputStep should succeed with valid name`() = runBlocking {
        // Given
        val parameters = createParameters("name" to "My Awesome World")

        // When
        val result = ValidateWorldNameInputStep.process(parameters)

        // Then
        assertIs<Result.Success<UpdateWorldNameInput>>(result)
        assertEquals("My Awesome World", result.value.name)
    }

    @Test
    fun `ValidateWorldNameInputStep should trim whitespace from name`() = runBlocking {
        // Given
        val parameters = createParameters("name" to "  Spaced World  ")

        // When
        val result = ValidateWorldNameInputStep.process(parameters)

        // Then
        assertIs<Result.Success<UpdateWorldNameInput>>(result)
        assertEquals("Spaced World", result.value.name)
    }

    @Test
    fun `ValidateWorldNameInputStep should fail with missing name`() = runBlocking {
        // Given
        val parameters = createParameters()

        // When
        val result = ValidateWorldNameInputStep.process(parameters)

        // Then
        assertIs<Result.Failure<UpdateWorldNameFailures.ValidationError>>(result)
        assertTrue(result.error.errors.any { it is ValidationFailure.MissingParameter && it.parameterName == "name" })
    }

    @Test
    fun `ValidateWorldNameInputStep should fail with empty name`() = runBlocking {
        // Given
        val parameters = createParameters("name" to "")

        // When
        val result = ValidateWorldNameInputStep.process(parameters)

        // Then
        assertIs<Result.Failure<UpdateWorldNameFailures.ValidationError>>(result)
        assertTrue(result.error.errors.isNotEmpty())
    }

    @ParameterizedTest
    @ValueSource(strings = ["ab", "a", ""]) // Names too short
    fun `ValidateWorldNameInputStep should fail with names shorter than 3 characters`(shortName: String) = runBlocking {
        // Given
        val parameters = createParameters("name" to shortName)

        // When
        val result = ValidateWorldNameInputStep.process(parameters)

        // Then
        assertIs<Result.Failure<UpdateWorldNameFailures.ValidationError>>(result)
        assertTrue(result.error.errors.isNotEmpty())
    }

    @Test
    fun `ValidateWorldNameInputStep should fail with name longer than 100 characters`() = runBlocking {
        // Given
        val longName = "a".repeat(101) // 101 characters
        val parameters = createParameters("name" to longName)

        // When
        val result = ValidateWorldNameInputStep.process(parameters)

        // Then
        assertIs<Result.Failure<UpdateWorldNameFailures.ValidationError>>(result)
        assertTrue(result.error.errors.any {
            it is ValidationFailure.CustomValidation && it.message.contains("between 3 and 100 characters")
        })
    }

    @ParameterizedTest
    @ValueSource(strings = ["abc", "Test World", "My-World_123", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"]) // Valid names (last one is exactly 100 chars)
    fun `ValidateWorldNameInputStep should succeed with valid name lengths`(validName: String) = runBlocking {
        // Given
        val parameters = createParameters("name" to validName)

        // When
        val result = ValidateWorldNameInputStep.process(parameters)

        // Then
        assertIs<Result.Success<UpdateWorldNameInput>>(result)
        assertEquals(validName.trim(), result.value.name)
    }

    @Test
    fun `UpdateWorldNameInput should create correct data structure`() {
        // Given
        val name = "Test World Name"

        // When
        val input = UpdateWorldNameInput(name)

        // Then
        assertEquals(name, input.name)
    }

    @Test
    fun `UpdateWorldNameFailures ValidationError should contain error list`() {
        // Given
        val validationFailures = listOf(
            ValidationFailure.MissingParameter("name"),
            ValidationFailure.InvalidFormat("name", "Too short")
        )

        // When
        val failure = UpdateWorldNameFailures.ValidationError(validationFailures)

        // Then
        assertEquals(validationFailures, failure.errors)
        assertEquals(2, failure.errors.size)
    }
}
