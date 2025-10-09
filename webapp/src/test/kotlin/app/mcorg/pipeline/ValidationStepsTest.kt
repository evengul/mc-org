package app.mcorg.pipeline

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidationStepsTest {

    private fun createParameters(vararg pairs: Pair<String, String>): Parameters {
        val builder = ParametersBuilder()
        pairs.forEach { (key, value) -> builder.append(key, value) }
        return builder.build()
    }

    private fun errorMapper(failure: ValidationFailure): String =
        "Error: ${failure::class.simpleName} - ${when(failure) {
            is ValidationFailure.MissingParameter -> failure.parameterName
            is ValidationFailure.InvalidFormat -> "${failure.parameterName}: ${failure.message}"
            is ValidationFailure.OutOfRange -> "${failure.parameterName} (${failure.min}-${failure.max})"
            is ValidationFailure.InvalidLength -> "${failure.parameterName} (${failure.minLength}-${failure.maxLength})"
            is ValidationFailure.InvalidValue -> "${failure.parameterName}: ${failure.allowedValues}"
            is ValidationFailure.CustomValidation -> "${failure.parameterName}: ${failure.message}"
        }}"

    // Required parameter tests
    @Test
    fun `required - returns success when parameter exists and is not blank`() = runBlocking {
        val params = createParameters("name" to "John Doe")
        val step = ValidationSteps.required("name", ::errorMapper)

        val result = step.process(params)

        assertTrue(result is Result.Success)
        assertEquals("John Doe", result.value)
    }

    @Test
    fun `required - returns failure when parameter is missing`() = runBlocking {
        val params = createParameters()
        val step = ValidationSteps.required("name", ::errorMapper)

        val result = step.process(params)

        assertTrue(result is Result.Failure)
        assertTrue(result.error.contains("MissingParameter"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "  ", "\t", "\n"])
    fun `required - returns failure when parameter is blank`(blankValue: String) = runBlocking {
        val params = createParameters("name" to blankValue)
        val step = ValidationSteps.required("name", ::errorMapper)

        val result = step.process(params)

        assertTrue(result is Result.Failure)
        assertTrue(result.error.contains("MissingParameter"))
    }

    // Optional parameter tests
    @Test
    fun `optional - returns success with value when parameter exists`() = runBlocking {
        val params = createParameters("description" to "Test description")
        val step = ValidationSteps.optional("description")

        val result = step.process(params)

        assertTrue(result is Result.Success)
        assertEquals("Test description", result.value)
    }

    @Test
    fun `optional - returns success with null when parameter is missing`() = runBlocking {
        val params = createParameters()
        val step = ValidationSteps.optional("description")

        val result = step.process(params)

        assertTrue(result is Result.Success)
        assertNull(result.value)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "  "])
    fun `optional - returns success with null when parameter is blank`(blankValue: String) = runBlocking {
        val params = createParameters("description" to blankValue)
        val step = ValidationSteps.optional("description")

        val result = step.process(params)

        assertTrue(result is Result.Success)
        assertNull(result.value)
    }

    // Integer validation tests
    @Test
    fun `requiredInt - returns success when parameter is valid integer`() = runBlocking {
        val params = createParameters("age" to "25")
        val step = ValidationSteps.requiredInt("age", ::errorMapper)

        val result = step.process(params)

        assertTrue(result is Result.Success)
        assertEquals(25, result.value)
    }

    @Test
    fun `requiredInt - returns failure when parameter is missing`() = runBlocking {
        val params = createParameters()
        val step = ValidationSteps.requiredInt("age", ::errorMapper)

        val result = step.process(params)

        assertTrue(result is Result.Failure)
        assertTrue(result.error.contains("MissingParameter"))
    }

    @Test
    fun `requiredInt - returns failure when parameter is blank`() = runBlocking {
        val params = createParameters("age" to " ")
        val step = ValidationSteps.requiredInt("age", ::errorMapper)

        val result = step.process(params)

        assertTrue(result is Result.Failure)
        assertTrue(result.error.contains("MissingParameter"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["abc", "25.5", "25.0"])
    fun `requiredInt - returns failure when parameter is not valid integer`(invalidValue: String) = runBlocking {
        val params = createParameters("age" to invalidValue)
        val step = ValidationSteps.requiredInt("age", ::errorMapper)

        val result = step.process(params)

        assertTrue(result is Result.Failure)
        assertTrue(result.error.contains("InvalidFormat"))
    }

    @Test
    fun `optionalInt - returns success with value when parameter is valid integer`() = runBlocking {
        val params = createParameters("limit" to "100")
        val step = ValidationSteps.optionalInt("limit", ::errorMapper)

        val result = step.process(params)

        assertTrue(result is Result.Success)
        assertEquals(100, result.value)
    }

    @Test
    fun `optionalInt - returns success with null when parameter is missing`() = runBlocking {
        val params = createParameters()
        val step = ValidationSteps.optionalInt("limit", ::errorMapper)

        val result = step.process(params)

        assertTrue(result is Result.Success)
        assertNull(result.value)
    }

    // Long validation tests
    @Test
    fun `requiredLong - returns success when parameter is valid long`() = runBlocking {
        val params = createParameters("id" to "1234567890123")
        val step = ValidationSteps.requiredLong("id", ::errorMapper)

        val result = step.process(params)

        assertTrue(result is Result.Success)
        assertEquals(1234567890123L, result.value)
    }

    @Test
    fun `requiredLong - returns failure when parameter is not valid long`() = runBlocking {
        val params = createParameters("id" to "not-a-number")
        val step = ValidationSteps.requiredLong("id", ::errorMapper)

        val result = step.process(params)

        assertTrue(result is Result.Failure)
        assertTrue(result.error.contains("InvalidFormat"))
    }

    // Boolean validation tests
    @ParameterizedTest
    @MethodSource("booleanTrueValues")
    fun `requiredBoolean - returns true for valid true values`(value: String) = runBlocking {
        val params = createParameters("active" to value)
        val step = ValidationSteps.requiredBoolean("active", ::errorMapper)

        val result = step.process(params)

        assertTrue(result is Result.Success)
        assertEquals(true, result.value)
    }

    @ParameterizedTest
    @MethodSource("booleanFalseValues")
    fun `requiredBoolean - returns false for valid false values`(value: String) = runBlocking {
        val params = createParameters("active" to value)
        val step = ValidationSteps.requiredBoolean("active", ::errorMapper)

        val result = step.process(params)

        assertTrue(result is Result.Success)
        assertEquals(false, result.value)
    }

    @Test
    fun `requiredBoolean - returns failure for invalid boolean value`() = runBlocking {
        val params = createParameters("active" to "maybe")
        val step = ValidationSteps.requiredBoolean("active", ::errorMapper)

        val result = step.process(params)

        assertTrue(result is Result.Failure)
        assertTrue(result.error.contains("InvalidFormat"))
    }

    // Length validation tests
    @Test
    fun `validateLength - returns success when length is within bounds`() = runBlocking {
        val step = ValidationSteps.validateLength("username", 3, 20, ::errorMapper)

        val result = step.process("john_doe")

        assertTrue(result is Result.Success)
        assertEquals("john_doe", result.value)
    }

    @Test
    fun `validateLength - returns failure when length is below minimum`() = runBlocking {
        val step = ValidationSteps.validateLength("username", 5, 20, ::errorMapper)

        val result = step.process("ab")

        assertTrue(result is Result.Failure)
        assertTrue(result.error.contains("InvalidLength"))
    }

    @Test
    fun `validateLength - returns failure when length exceeds maximum`() = runBlocking {
        val step = ValidationSteps.validateLength("username", 3, 10, ::errorMapper)

        val result = step.process("this_is_too_long")

        assertTrue(result is Result.Failure)
        assertTrue(result.error.contains("InvalidLength"))
    }

    // Range validation tests
    @Test
    fun `validateRange - returns success when value is within range`() = runBlocking {
        val step = ValidationSteps.validateRange("score", 0, 100, ::errorMapper)

        val result = step.process(85)

        assertTrue(result is Result.Success)
        assertEquals(85, result.value)
    }

    @Test
    fun `validateRange - returns failure when value is below minimum`() = runBlocking {
        val step = ValidationSteps.validateRange("score", 0, 100, ::errorMapper)

        val result = step.process(-5)

        assertTrue(result is Result.Failure)
        assertTrue(result.error.contains("OutOfRange"))
    }

    @Test
    fun `validateRange - returns failure when value exceeds maximum`() = runBlocking {
        val step = ValidationSteps.validateRange("score", 0, 100, ::errorMapper)

        val result = step.process(150)

        assertTrue(result is Result.Failure)
        assertTrue(result.error.contains("OutOfRange"))
    }

    // Allowed values validation tests
    @Test
    fun `validateAllowedValues - returns success when value is allowed`() = runBlocking {
        val allowedValues = listOf("red", "green", "blue")
        val step = ValidationSteps.validateAllowedValues("color", allowedValues, ::errorMapper)

        val result = step.process("green")

        assertTrue(result is Result.Success)
        assertEquals("green", result.value)
    }

    @Test
    fun `validateAllowedValues - returns failure when value is not allowed`() = runBlocking {
        val allowedValues = listOf("red", "green", "blue")
        val step = ValidationSteps.validateAllowedValues("color", allowedValues, ::errorMapper)

        val result = step.process("yellow")

        assertTrue(result is Result.Failure)
        assertTrue(result.error.contains("InvalidValue"))
    }

    @Test
    fun `validateAllowedValues - handles case insensitive matching`() = runBlocking {
        val allowedValues = listOf("RED", "GREEN", "BLUE")
        val step = ValidationSteps.validateAllowedValues("color", allowedValues, ::errorMapper, ignoreCase = true)

        val result = step.process("red")

        assertTrue(result is Result.Success)
        assertEquals("red", result.value)
    }

    // Custom validation tests
    @Test
    fun `validateCustom - returns success when predicate is true`() = runBlocking {
        val step = ValidationSteps.validateCustom<String, String>(
            "password",
            "Must contain at least one digit",
            ::errorMapper
        ) { it.any { char -> char.isDigit() } }

        val result = step.process("password123")

        assertTrue(result is Result.Success)
        assertEquals("password123", result.value)
    }

    @Test
    fun `validateCustom - returns failure when predicate is false`() = runBlocking {
        val step = ValidationSteps.validateCustom<String, String>(
            "password",
            "Must contain at least one digit",
            ::errorMapper
        ) { it.any { char -> char.isDigit() } }

        val result = step.process("password")

        assertTrue(result is Result.Failure)
        assertTrue(result.error.contains("CustomValidation"))
    }

    // Email validation tests
    @ParameterizedTest
    @ValueSource(strings = ["user@example.com", "test.email@domain.co.uk", "user+tag@example.org"])
    fun `validateEmail - returns success for valid emails`(email: String) = runBlocking {
        val step = ValidationSteps.validateEmail("email", ::errorMapper)

        val result = step.process(email)

        assertTrue(result is Result.Success)
        assertEquals(email, result.value)
    }

    @ParameterizedTest
    @ValueSource(strings = ["invalid-email", "@example.com", "user@"])
    fun `validateEmail - returns failure for invalid emails`(email: String) = runBlocking {
        val step = ValidationSteps.validateEmail("email", ::errorMapper)

        val result = step.process(email)

        assertTrue(result is Result.Failure)
        assertTrue(result.error.contains("CustomValidation"))
    }

    // URL validation tests
    @ParameterizedTest
    @ValueSource(strings = ["http://example.com", "https://www.example.com/path", "https://api.example.com/v1"])
    fun `validateUrl - returns success for valid URLs`(url: String) = runBlocking {
        val step = ValidationSteps.validateUrl("url", ::errorMapper)

        val result = step.process(url)

        assertTrue(result is Result.Success)
        assertEquals(url, result.value)
    }

    @ParameterizedTest
    @ValueSource(strings = ["ftp://example.com", "invalid-url"])
    fun `validateUrl - returns failure for invalid or disallowed URLs`(url: String) = runBlocking {
        val step = ValidationSteps.validateUrl("url", ::errorMapper)

        val result = step.process(url)

        assertTrue(result is Result.Failure)
        assertTrue(result.error.contains("CustomValidation"))
    }

    // UUID validation tests
    @Test
    fun `validateUuid - returns success for valid UUID`() = runBlocking {
        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        val step = ValidationSteps.validateUuid("id", ::errorMapper)

        val result = step.process(uuid)

        assertTrue(result is Result.Success)
        assertEquals(uuid, result.value)
    }

    @ParameterizedTest
    @ValueSource(strings = ["invalid-uuid", "550e8400-e29b-41d4-a716", "not-a-uuid-at-all"])
    fun `validateUuid - returns failure for invalid UUIDs`(uuid: String) = runBlocking {
        val step = ValidationSteps.validateUuid("id", ::errorMapper)

        val result = step.process(uuid)

        assertTrue(result is Result.Failure)
        assertTrue(result.error.contains("CustomValidation"))
    }

    // Test data providers
    @Suppress("unused")
    private fun booleanTrueValues(): Stream<Arguments> = Stream.of(
        Arguments.of("true"),
        Arguments.of("TRUE"),
        Arguments.of("True"),
        Arguments.of("1"),
        Arguments.of("yes"),
        Arguments.of("YES"),
        Arguments.of("on"),
        Arguments.of("ON")
    )

    @Suppress("unused")
    private fun booleanFalseValues(): Stream<Arguments> = Stream.of(
        Arguments.of("false"),
        Arguments.of("FALSE"),
        Arguments.of("False"),
        Arguments.of("0"),
        Arguments.of("no"),
        Arguments.of("NO"),
        Arguments.of("off"),
        Arguments.of("OFF")
    )
}
