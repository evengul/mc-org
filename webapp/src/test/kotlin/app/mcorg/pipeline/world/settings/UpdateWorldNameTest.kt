package app.mcorg.pipeline.world.settings

import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.world.settings.general.ValidateWorldNameInputStep
import app.mcorg.test.utils.TestUtils
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
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
        val result = TestUtils.executeAndAssertSuccess(
            ValidateWorldNameInputStep,
            parameters
        )

        // Then
        assertEquals("My Awesome World", result)
    }

    @Test
    fun `ValidateWorldNameInputStep should trim whitespace from name`() = runBlocking {
        // Given
        val parameters = createParameters("name" to "  Spaced World  ")

        // When
        val result = TestUtils.executeAndAssertSuccess(
            ValidateWorldNameInputStep,
            parameters
        )

        // Then
        assertEquals("Spaced World", result)
    }

    @Test
    fun `ValidateWorldNameInputStep should fail with missing name`() = runBlocking {
        // Given
        val parameters = createParameters()

        // When
        val result = TestUtils.executeAndAssertFailure(
            ValidateWorldNameInputStep,
            parameters
        )

        // Then
        assertTrue(result.errors.any { it is ValidationFailure.MissingParameter && it.parameterName == "name" })
    }

    @Test
    fun `ValidateWorldNameInputStep should fail with empty name`() = runBlocking {
        // Given
        val parameters = createParameters("name" to "")

        // When
        val result = TestUtils.executeAndAssertFailure(
            ValidateWorldNameInputStep,
            parameters
        )

        // Then
        assertTrue(result.errors.isNotEmpty())
    }

    @ParameterizedTest
    @ValueSource(strings = ["ab", "a", ""]) // Names too short
    fun `ValidateWorldNameInputStep should fail with names shorter than 3 characters`(shortName: String) = runBlocking {
        // Given
        val parameters = createParameters("name" to shortName)

        // When
        val result = TestUtils.executeAndAssertFailure(
            ValidateWorldNameInputStep,
            parameters
        )

        // Then
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `ValidateWorldNameInputStep should fail with name longer than 100 characters`() = runBlocking {
        // Given
        val longName = "a".repeat(101) // 101 characters
        val parameters = createParameters("name" to longName)

        // When
        val result = TestUtils.executeAndAssertFailure(
            ValidateWorldNameInputStep,
            parameters
        )

        // Then
        assertTrue(result.errors.any {
            it is ValidationFailure.CustomValidation && it.message.contains("between 3 and 100 characters")
        })
    }

    @ParameterizedTest
    @ValueSource(strings = ["abc", "Test World", "My-World_123", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"]) // Valid names (last one is exactly 100 chars)
    fun `ValidateWorldNameInputStep should succeed with valid name lengths`(validName: String) = runBlocking {
        // Given
        val parameters = createParameters("name" to validName)

        // When
        val result = TestUtils.executeAndAssertSuccess(
            ValidateWorldNameInputStep,
            parameters
        )

        // Then
        assertEquals(validName.trim(), result)
    }
}
