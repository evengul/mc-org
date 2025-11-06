package app.mcorg.pipeline.world.settings

import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.world.settings.general.ValidateWorldDescriptionInputStep
import app.mcorg.test.utils.TestUtils
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
        val result = TestUtils.executeAndAssertSuccess(
            ValidateWorldDescriptionInputStep,
            parameters
        )

        // Then
        assertEquals("This is a detailed description of my world", result)
    }

    @Test
    fun `ValidateWorldDescriptionInputStep should trim whitespace from description`() = runBlocking {
        // Given
        val parameters = createParameters("description" to "  Spaced Description  ")

        // When
        val result = TestUtils.executeAndAssertSuccess(
            ValidateWorldDescriptionInputStep,
            parameters
        )

        // Then
        assertEquals("Spaced Description", result)
    }

    @Test
    fun `ValidateWorldDescriptionInputStep should succeed with empty description (optional field)`() = runBlocking {
        // Given
        val parameters = createParameters()

        // When
        val result = TestUtils.executeAndAssertSuccess(
            ValidateWorldDescriptionInputStep,
            parameters
        )

        // Then
        assertEquals("", result)
    }

    @Test
    fun `ValidateWorldDescriptionInputStep should succeed with explicit empty description`() = runBlocking {
        // Given
        val parameters = createParameters("description" to "")

        // When
        val result = TestUtils.executeAndAssertSuccess(
            ValidateWorldDescriptionInputStep,
            parameters
        )

        // Then
        assertEquals("", result)
    }

    @Test
    fun `ValidateWorldDescriptionInputStep should fail with description longer than 1000 characters`() {
        // Given
        val longDescription = "a".repeat(1001) // 1001 characters
        val parameters = createParameters("description" to longDescription)

        // When
        val result = TestUtils.executeAndAssertFailure(
            ValidateWorldDescriptionInputStep,
            parameters
        )

        // Then
        assertEquals(1, result.errors.size)
        assertIs<ValidationFailure.InvalidLength>(result.errors.first())
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
        val result = TestUtils.executeAndAssertSuccess(
            ValidateWorldDescriptionInputStep,
            parameters
        )

        // Then
        assertEquals(validDescription.trim(), result)
    }
}
