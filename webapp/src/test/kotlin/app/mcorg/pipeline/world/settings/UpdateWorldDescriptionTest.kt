package app.mcorg.pipeline.world.settings

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UpdateWorldDescriptionTest {

    private fun createParameters(vararg pairs: Pair<String, String>): Parameters {
        val builder = ParametersBuilder()
        pairs.forEach { (key, value) -> builder.append(key, value) }
        return builder.build()
    }

    @Test
    fun `ValidateWorldDescriptionInputStep should succeed with valid description`() = runBlocking {
        // Given
        val parameters = createParameters("description" to "This is a detailed description of my world")

        // When
        val result = ValidateWorldDescriptionInputStep.process(parameters)

        // Then
        assertIs<Result.Success<UpdateWorldDescriptionInput>>(result)
        assertEquals("This is a detailed description of my world", result.value.description)
    }

    @Test
    fun `ValidateWorldDescriptionInputStep should trim whitespace from description`() = runBlocking {
        // Given
        val parameters = createParameters("description" to "  Spaced Description  ")

        // When
        val result = ValidateWorldDescriptionInputStep.process(parameters)

        // Then
        assertIs<Result.Success<UpdateWorldDescriptionInput>>(result)
        assertEquals("Spaced Description", result.value.description)
    }

    @Test
    fun `ValidateWorldDescriptionInputStep should succeed with empty description (optional field)`() = runBlocking {
        // Given
        val parameters = createParameters()

        // When
        val result = ValidateWorldDescriptionInputStep.process(parameters)

        // Then
        assertIs<Result.Success<UpdateWorldDescriptionInput>>(result)
        assertEquals("", result.value.description)
    }

    @Test
    fun `ValidateWorldDescriptionInputStep should succeed with explicit empty description`() = runBlocking {
        // Given
        val parameters = createParameters("description" to "")

        // When
        val result = ValidateWorldDescriptionInputStep.process(parameters)

        // Then
        assertIs<Result.Success<UpdateWorldDescriptionInput>>(result)
        assertEquals("", result.value.description)
    }

    @Test
    fun `ValidateWorldDescriptionInputStep should fail with description longer than 1000 characters`() = runBlocking {
        // Given
        val longDescription = "a".repeat(1001) // 1001 characters
        val parameters = createParameters("description" to longDescription)

        // When
        val result = ValidateWorldDescriptionInputStep.process(parameters)

        // Then
        assertIs<Result.Failure<UpdateWorldDescriptionFailures.ValidationError>>(result)
        assertTrue(result.error.errors.any {
            it is ValidationFailure.CustomValidation && it.message.contains("1000 characters or less")
        })
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "Short description",
        "This is a medium length description with some details about the world",
        "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean massa. Cum sociis natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Donec quam felis, ultricies nec, pellentesque eu, pretium quis, sem. Nulla consequat massa quis enim. Donec pede justo, fringilla vel, aliquet nec, vulputate eget, arcu. In enim justo, rhoncus ut, imperdiet a, venenatis vitae, justo. Nullam dictum felis eu pede mollis pretium. Integer tincidunt. Cras dapibus. Vivamus elementum semper nisi. Aenean vulputate eleifend tellus. Aenean leo ligula, porttitor eu, consequat vitae, eleifend ac, enim. Aliquam lorem ante, dapibus in, viverra quis, feugiat a, tellus. Phasellus viverra nulla ut metus varius laoreet. Quisque rutrum. Aenean imperdiet. Etiam ultricies nisi vel augue. Curabitur ullamcorper ultricies nisi. Nam eget dui. Etiam rhoncus. Maecenas tempus, tellus eget condimentum rhoncus, sem quam semper libero, sit amet adipiscing sem neque sed ipsum. N" // Exactly 1000 chars
    ])
    fun `ValidateWorldDescriptionInputStep should succeed with valid description lengths`(validDescription: String) = runBlocking {
        // Given
        val parameters = createParameters("description" to validDescription)

        // When
        val result = ValidateWorldDescriptionInputStep.process(parameters)

        // Then
        assertIs<Result.Success<UpdateWorldDescriptionInput>>(result)
        assertEquals(validDescription.trim(), result.value.description)
    }

    @Test
    fun `UpdateWorldDescriptionInput should create correct data structure`() {
        // Given
        val description = "Test world description"

        // When
        val input = UpdateWorldDescriptionInput(description)

        // Then
        assertEquals(description, input.description)
    }

    @Test
    fun `UpdateWorldDescriptionFailures ValidationError should contain error list`() {
        // Given
        val validationFailures = listOf(
            ValidationFailure.CustomValidation("description", "Too long"),
            ValidationFailure.InvalidFormat("description", "Invalid format")
        )

        // When
        val failure = UpdateWorldDescriptionFailures.ValidationError(validationFailures)

        // Then
        assertEquals(validationFailures, failure.errors)
        assertEquals(2, failure.errors.size)
    }

    @Test
    fun `UpdateWorldDescriptionFailures should have correct sealed interface structure`() {
        // Test that all failure types can be created
        val validationError = UpdateWorldDescriptionFailures.ValidationError(emptyList())
        val databaseError = UpdateWorldDescriptionFailures.DatabaseError(
            app.mcorg.pipeline.failure.DatabaseFailure.NotFound
        )

        // Verify all are instances of UpdateWorldDescriptionFailures
        assertIs<UpdateWorldDescriptionFailures>(validationError)
        assertIs<UpdateWorldDescriptionFailures>(databaseError)
    }
}
