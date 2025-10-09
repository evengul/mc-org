package app.mcorg.domain.pipeline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class PipelineBuilderTest {

    // Test data classes
    data class TestError(val message: String)
    data class ValidationError(val field: String, val reason: String)

    @Test
    fun `pipeline factory function should create PipelineBuilder with identity pipeline`() {
        val builder = pipeline<String, TestError>()

        runBlocking {
            var successValue: String? = null
            var failureCalled = false

            builder.execute(
                input = "test",
                onSuccess = { successValue = it },
                onFailure = { failureCalled = true }
            )

            assertEquals("test", successValue)
            assertFalse(failureCalled)
        }
    }

    @Test
    fun `step should chain Steps in PipelineBuilder`() {
        val lengthStep = object : Step<String, TestError, Int> {
            override suspend fun process(input: String): Result<TestError, Int> {
                return Result.Success(input.length)
            }
        }

        val builder = pipeline<String, TestError>()
            .step(lengthStep)

        runBlocking {
            var result: Int? = null
            var failureCalled = false

            builder.execute(
                input = "hello",
                onSuccess = { result = it },
                onFailure = { failureCalled = true }
            )

            assertEquals(5, result)
            assertFalse(failureCalled)
        }
    }

    @Test
    fun `step should handle Step failure in chain`() {
        val failingStep = object : Step<String, TestError, Int> {
            override suspend fun process(input: String): Result<TestError, Int> {
                return Result.Failure(TestError("Step failed"))
            }
        }

        val builder = pipeline<String, TestError>()
            .step(failingStep)

        runBlocking {
            var successCalled = false
            var error: TestError? = null

            builder.execute(
                input = "test",
                onSuccess = { successCalled = true },
                onFailure = { error = it }
            )

            assertFalse(successCalled)
            assertEquals("Step failed", error?.message)
        }
    }

    @Test
    fun `validate should add validation step that succeeds when predicate is true`() {
        val builder = pipeline<String, TestError>()
            .validate(TestError("String too short")) { it.length >= 3 }

        runBlocking {
            var result: String? = null
            var failureCalled = false

            builder.execute(
                input = "hello",
                onSuccess = { result = it },
                onFailure = { failureCalled = true }
            )

            assertEquals("hello", result)
            assertFalse(failureCalled)
        }
    }

    @Test
    fun `validate should add validation step that fails when predicate is false`() {
        val error = TestError("String too short")
        val builder = pipeline<String, TestError>()
            .validate(error) { it.length >= 10 }

        runBlocking {
            var successCalled = false
            var failureError: TestError? = null

            builder.execute(
                input = "hello",
                onSuccess = { successCalled = true },
                onFailure = { failureError = it }
            )

            assertFalse(successCalled)
            assertEquals(error, failureError)
        }
    }

    @Test
    fun `transform should apply transformation function to pipeline output`() {
        val builder = pipeline<String, TestError>()
            .transform { it.uppercase() }

        runBlocking {
            var result: String? = null
            var failureCalled = false

            builder.execute(
                input = "hello",
                onSuccess = { result = it },
                onFailure = { failureCalled = true }
            )

            assertEquals("HELLO", result)
            assertFalse(failureCalled)
        }
    }

    @Test
    fun `transform should handle transformation with different output type`() {
        val builder = pipeline<String, TestError>()
            .transform { it.length }

        runBlocking {
            var result: Int? = null
            var failureCalled = false

            builder.execute(
                input = "hello",
                onSuccess = { result = it },
                onFailure = { failureCalled = true }
            )

            assertEquals(5, result)
            assertFalse(failureCalled)
        }
    }

    @Test
    fun `multiple operations should chain correctly`() {
        val doubleStep = object : Step<Int, TestError, Int> {
            override suspend fun process(input: Int): Result<TestError, Int> {
                return Result.Success(input * 2)
            }
        }

        val builder = pipeline<String, TestError>()
            .transform { it.length }
            .step(doubleStep)
            .validate(TestError("Result too small")) { it >= 8 }
            .transform { "Result: $it" }

        runBlocking {
            var result: String? = null
            var failureCalled = false

            builder.execute(
                input = "hello",
                onSuccess = { result = it },
                onFailure = { failureCalled = true }
            )

            assertEquals("Result: 10", result) // "hello".length * 2 = 10
            assertFalse(failureCalled)
        }
    }

    @Test
    fun `pipeline should short-circuit on validation failure`() {
        var transformCalled = false

        val builder = pipeline<String, TestError>()
            .validate(TestError("Too short")) { it.length >= 10 }
            .transform {
                transformCalled = true
                it.uppercase()
            }

        runBlocking {
            var successCalled = false
            var error: TestError? = null

            builder.execute(
                input = "hi",
                onSuccess = { successCalled = true },
                onFailure = { error = it }
            )

            assertFalse(successCalled)
            assertFalse(transformCalled) // Should not be called due to validation failure
            assertEquals("Too short", error?.message)
        }
    }

    @Test
    fun `pipeline should short-circuit on step failure`() {
        val failingStep = object : Step<String, TestError, Int> {
            override suspend fun process(input: String): Result<TestError, Int> {
                return Result.Failure(TestError("Processing failed"))
            }
        }

        var transformCalled = false

        val builder = pipeline<String, TestError>()
            .step(failingStep)
            .transform {
                transformCalled = true
                it * 2
            }

        runBlocking {
            var successCalled = false
            var error: TestError? = null

            builder.execute(
                input = "test",
                onSuccess = { successCalled = true },
                onFailure = { error = it }
            )

            assertFalse(successCalled)
            assertFalse(transformCalled) // Should not be called due to step failure
            assertEquals("Processing failed", error?.message)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "hello", "test", "pipeline"])
    fun `pipeline should handle various string inputs`(input: String) {
        val builder = pipeline<String, TestError>()
            .transform { it.length }
            .validate(TestError("Length must be positive")) { it > 0 }

        runBlocking {
            var result: Int? = null
            var failureCalled = false

            builder.execute(
                input = input,
                onSuccess = { result = it },
                onFailure = { failureCalled = true }
            )

            assertEquals(input.length, result)
            assertFalse(failureCalled)
        }
    }

    @Test
    fun `execute should handle async operations correctly`() {
        val asyncStep = object : Step<String, TestError, String> {
            override suspend fun process(input: String): Result<TestError, String> {
                kotlinx.coroutines.delay(10) // Simulate async operation
                return Result.Success(input.reversed())
            }
        }

        val builder = pipeline<String, TestError>()
            .step(asyncStep)
            .transform { it.uppercase() }

        runBlocking {
            var result: String? = null
            var failureCalled = false

            builder.execute(
                input = "hello",
                onSuccess = { result = it },
                onFailure = { failureCalled = true }
            )

            assertEquals("OLLEH", result)
            assertFalse(failureCalled)
        }
    }

    @Test
    fun `complex business logic pipeline should work correctly`() {
        data class User(val name: String, val age: Int, val email: String)
        data class ValidatedUser(val name: String, val age: Int, val email: String, val isValid: Boolean)

        val parseUserStep = object : Step<String, TestError, User> {
            override suspend fun process(input: String): Result<TestError, User> {
                val parts = input.split(",")
                return if (parts.size == 3) {
                    val age = parts[1].toIntOrNull()
                    if (age != null) {
                        Result.Success(User(parts[0], age, parts[2]))
                    } else {
                        Result.Failure(TestError("Invalid age format"))
                    }
                } else {
                    Result.Failure(TestError("Invalid user format"))
                }
            }
        }

        val builder = pipeline<String, TestError>()
            .step(parseUserStep)
            .validate(TestError("Age must be positive")) { it.age > 0 }
            .validate(TestError("Email must contain @")) { it.email.contains("@") }
            .transform { ValidatedUser(it.name, it.age, it.email, true) }

        runBlocking {
            var result: ValidatedUser? = null
            var failureCalled = false

            builder.execute(
                input = "John,25,john@example.com",
                onSuccess = { result = it },
                onFailure = { failureCalled = true }
            )

            assertEquals("John", result?.name)
            assertEquals(25, result?.age)
            assertEquals("john@example.com", result?.email)
            assertTrue(result?.isValid == true)
            assertFalse(failureCalled)
        }
    }

    @Test
    fun `complex pipeline should fail at appropriate validation step`() {
        data class User(val name: String, val age: Int, val email: String)

        val parseUserStep = object : Step<String, TestError, User> {
            override suspend fun process(input: String): Result<TestError, User> {
                val parts = input.split(",")
                return if (parts.size == 3) {
                    val age = parts[1].toIntOrNull()
                    if (age != null) {
                        Result.Success(User(parts[0], age, parts[2]))
                    } else {
                        Result.Failure(TestError("Invalid age format"))
                    }
                } else {
                    Result.Failure(TestError("Invalid user format"))
                }
            }
        }

        val builder = pipeline<String, TestError>()
            .step(parseUserStep)
            .validate(TestError("Age must be at least 18")) { it.age >= 18 }
            .validate(TestError("Email must contain @")) { it.email.contains("@") }

        runBlocking {
            var successCalled = false
            var error: TestError? = null

            builder.execute(
                input = "Alice,16,alice@example.com", // Should fail age validation
                onSuccess = { successCalled = true },
                onFailure = { error = it }
            )

            assertFalse(successCalled)
            assertEquals("Age must be at least 18", error?.message)
        }
    }

    @Test
    fun `pipeline builder should handle different error types through type system`() {
        val validationError = ValidationError("field", "reason")

        // This tests type compatibility
        val builder = pipeline<String, ValidationError>()
            .validate(validationError) { it.isNotEmpty() }
            .transform { it.uppercase() }

        runBlocking {
            var successCalled = false
            var error: ValidationError? = null

            builder.execute(
                input = "",
                onSuccess = { successCalled = true },
                onFailure = { error = it }
            )

            assertFalse(successCalled)
            assertEquals(validationError, error)
        }
    }

    @Test
    fun `pipeline builder should work with nullable types`() {
        val builder = pipeline<String?, TestError>()
            .validate(TestError("Input cannot be null")) { it != null }
            .transform { it!!.uppercase() } // Safe because of validation

        runBlocking {
            var result: String? = null
            var failureCalled = false

            builder.execute(
                input = "hello",
                onSuccess = { result = it },
                onFailure = { failureCalled = true }
            )

            assertEquals("HELLO", result)
            assertFalse(failureCalled)
        }
    }

    @Test
    fun `pipeline builder should handle null input validation failure`() {
        val builder = pipeline<String?, TestError>()
            .validate(TestError("Input cannot be null")) { it != null }
            .transform { it!!.length }

        runBlocking {
            var successCalled = false
            var error: TestError? = null

            builder.execute(
                input = null,
                onSuccess = { successCalled = true },
                onFailure = { error = it }
            )

            assertFalse(successCalled)
            assertEquals("Input cannot be null", error?.message)
        }
    }
}
