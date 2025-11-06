package app.mcorg.pipeline.world.settings

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.world.settings.general.ValidateWorldVersionInputStep
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
class UpdateWorldVersionTest {

    private fun createParameters(vararg pairs: Pair<String, String>): Parameters {
        val builder = ParametersBuilder()
        pairs.forEach { (key, value) -> builder.append(key, value) }
        return builder.build()
    }

    @Test
    fun `ValidateWorldVersionInputStep should succeed with valid release version`() = runBlocking {
        // Given
        val parameters = createParameters("version" to "1.20.1")

        // When
        val result = TestUtils.executeAndAssertSuccess(
            ValidateWorldVersionInputStep,
            parameters
        )

        // Then
        assertEquals(MinecraftVersion.fromString("1.20.1"), result)
    }

    @Test
    fun `ValidateWorldVersionInputStep should succeed with valid snapshot version`() = runBlocking {
        // Given
        val parameters = createParameters("version" to "23w31a")

        // When
        val result = TestUtils.executeAndAssertSuccess(
            ValidateWorldVersionInputStep,
            parameters
        )

        // Then
        assertEquals(MinecraftVersion.fromString("23w31a"), result)
    }

    @Test
    fun `ValidateWorldVersionInputStep should trim whitespace from version`() = runBlocking {
        // Given
        val parameters = createParameters("version" to "  1.19.4  ")

        // When
        val result = TestUtils.executeAndAssertSuccess(
            ValidateWorldVersionInputStep,
            parameters
        )

        // Then
        assertEquals(MinecraftVersion.fromString("1.19.4"), result)
    }

    @Test
    fun `ValidateWorldVersionInputStep should fail with missing version`() = runBlocking {
        // Given
        val parameters = createParameters()

        // When
        val result = TestUtils.executeAndAssertFailure(
            ValidateWorldVersionInputStep,
            parameters
        )

        // Then
        assertTrue(result.errors.any { it is ValidationFailure.MissingParameter && it.parameterName == "version" })
    }

    @Test
    fun `ValidateWorldVersionInputStep should fail with empty version`() = runBlocking {
        // Given
        val parameters = createParameters("version" to "")

        // When
        val result = TestUtils.executeAndAssertFailure(
            ValidateWorldVersionInputStep,
            parameters
        )

        // Then
        assertTrue(result.errors.isNotEmpty())
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "invalid.version",
        "not-a-version",
        "1.2.3.4",
        "snapshot-invalid",
        "99w99z"
    ]) // Invalid version formats
    fun `ValidateWorldVersionInputStep should fail with invalid version formats`(invalidVersion: String) = runBlocking {
        // Given
        val parameters = createParameters("version" to invalidVersion)

        // When
        val result = TestUtils.executeAndAssertFailure(
            ValidateWorldVersionInputStep,
            parameters
        )

        // Then
        assertTrue(result.errors.any {
            it is ValidationFailure.CustomValidation && it.message.contains("Invalid Minecraft version format")
        })
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "1.20.1",
        "1.19.4",
        "1.18.2",
        "1.17.1",
        "23w31a",
        "23w07a",
        "22w46a"
    ]) // Valid version formats
    fun `ValidateWorldVersionInputStep should succeed with valid version formats`(validVersion: String) = runBlocking {
        // Given
        val parameters = createParameters("version" to validVersion)

        // When
        val result = TestUtils.executeAndAssertSuccess(
            ValidateWorldVersionInputStep,
            parameters
        )

        // Then
        assertEquals(MinecraftVersion.fromString(validVersion), result)
    }

    @Test
    fun `ValidateWorldVersionInputStep should fail gracefully with null-like inputs`() = runBlocking {
        // Given
        val parameters = createParameters("version" to "null")

        // When
        val result = TestUtils.executeAndAssertFailure(
            ValidateWorldVersionInputStep,
            parameters
        )

        // Then
        assertTrue(result.errors.any {
            it is ValidationFailure.CustomValidation && it.message.contains("Invalid Minecraft version format")
        })
    }
}
