package app.mcorg.domain.pipeline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

class ResultTest {

    // Test data classes
    data class TestError(val message: String)

    @Test
    fun `Success should create Success instance with value`() {
        val value = "test value"
        val result = Result.Success(value)

        assertEquals(value, result.value)
        assertIs<Result.Success<String>>(result)
    }

    @Test
    fun `Failure should create Failure instance with error`() {
        val error = TestError("test error")
        val result = Result.Failure(error)

        assertEquals(error, result.error)
        assertIs<Result.Failure<TestError>>(result)
    }

    @Test
    fun `getOrNull should return value for Success`() {
        val value = "test value"
        val result = Result.Success(value)

        assertEquals(value, result.getOrNull())
    }

    @Test
    fun `getOrNull should return null for Failure`() {
        val result = Result.Failure(TestError("error"))

        assertNull(result.getOrNull())
    }

    @Test
    fun `map should transform Success value`() = runBlocking {
        val result = Result.Success("hello")
        val mapped = result.map { it.uppercase() }

        assertIs<Result.Success<String>>(mapped)
        assertEquals("HELLO", mapped.value)
    }

    @Test
    fun `map should preserve Failure`() = runBlocking {
        val error = TestError("error")
        val result: Result<TestError, String> = Result.Failure(error)
        val mapped = result.map { it.uppercase() }

        assertIs<Result.Failure<TestError>>(mapped)
        assertEquals(error, mapped.error)
    }

    @Test
    fun `map should handle transformation exceptions`() {
        val result = Result.Success("test")

        assertThrows<RuntimeException> {
            runBlocking {
                result.map { throw RuntimeException("Transform error") }
            }
        }
    }

    @Test
    fun `peek should call function with Success value and return same result`() {
        runBlocking {
            val result = Result.Success("test")
            var peekedValue: String? = null

            val peekedResult = result.peek { peekedValue = it }

            assertEquals("test", peekedValue)
            assertEquals(result, peekedResult)
            assertIs<Result.Success<String>>(peekedResult)
        }
    }

    @Test
    fun `peek should not call function for Failure and return same result`() {
        runBlocking {
            val error = TestError("error")
            val result: Result<TestError, String> = Result.Failure(error)
            var wasCalled = false

            val peekedResult = result.peek { wasCalled = true }

            assertEquals(false, wasCalled)
            assertEquals(result, peekedResult)
            assertIs<Result.Failure<TestError>>(peekedResult)
        }
    }

    @Test
    fun `flatMap should chain Success results`() = runBlocking {
        val result: Result<TestError, Int> = Result.Success(5)
        val chained = result.flatMap { value ->
            if (value > 0) Result.Success("positive: $value")
            else Result.Failure(TestError("negative"))
        }

        assertIs<Result.Success<String>>(chained)
        assertEquals("positive: 5", chained.value)
    }

    @Test
    fun `flatMap should chain to Failure when transform returns Failure`() = runBlocking {
        val result: Result<TestError, Int> = Result.Success(-5)
        val chained = result.flatMap { value ->
            if (value > 0) Result.Success("positive: $value")
            else Result.Failure(TestError("negative"))
        }

        assertIs<Result.Failure<TestError>>(chained)
        assertEquals("negative", chained.error.message)
    }

    @Test
    fun `flatMap should preserve original Failure`() = runBlocking {
        val originalError = TestError("original error")
        val result: Result<TestError, Int> = Result.Failure(originalError)
        val chained = result.flatMap { value ->
            Result.Success("transformed: $value")
        }

        assertIs<Result.Failure<TestError>>(chained)
        assertEquals(originalError, chained.error)
    }

    @Test
    fun `mapError should transform Failure error`() = runBlocking {
        val result: Result<TestError, String> = Result.Failure(TestError("original"))
        val mapped = result.mapError { error ->
            "Mapped: ${error.message}"
        }

        assertIs<Result.Failure<String>>(mapped)
        assertEquals("Mapped: original", mapped.error)
    }

    @Test
    fun `mapError should preserve Success`() = runBlocking {
        val result: Result<TestError, String> = Result.Success("value")
        val mapped = result.mapError { error ->
            "Mapped: ${error.message}"
        }

        assertIs<Result.Success<String>>(mapped)
        assertEquals("value", mapped.value)
    }

    @Test
    fun `recover should transform Failure to Success`() = runBlocking {
        val result: Result<TestError, String> = Result.Failure(TestError("error"))
        val recovered = result.recover { error ->
            Result.Success("recovered from: ${error.message}")
        }

        assertIs<Result.Success<String>>(recovered)
        assertEquals("recovered from: error", recovered.value)
    }

    @Test
    fun `recover should transform Failure to another Failure`() = runBlocking {
        val result: Result<TestError, String> = Result.Failure(TestError("error"))
        val recovered = result.recover { error ->
            Result.Failure(TestError("new error: ${error.message}"))
        }

        assertIs<Result.Failure<TestError>>(recovered)
        assertEquals("new error: error", recovered.error.message)
    }

    @Test
    fun `recover should preserve Success`() = runBlocking {
        val result: Result<TestError, String> = Result.Success("value")
        val recovered = result.recover { error ->
            Result.Success("recovered from: ${error.message}")
        }

        assertIs<Result.Success<String>>(recovered)
        assertEquals("value", recovered.value)
    }

    // Edge cases and complex scenarios
    @Test
    fun `chaining multiple operations on Success`() = runBlocking {
        val step1: Result<TestError, Int> = Result.Success(10)
        val step2 = step1.map { it * 2 }
        val step3 = step2.flatMap { value ->
            if (value > 15) Result.Success("Large: $value")
            else Result.Failure(TestError("Too small"))
        }
        val result = step3.peek { value -> println("Final value: $value") }

        assertIs<Result.Success<String>>(result)
        assertEquals("Large: 20", result.value)
    }

    @Test
    fun `chaining operations should short-circuit on Failure`() = runBlocking {
        var mapCalled = false
        var peekCalled = false

        val result: Result<TestError, Int> = Result.Failure(TestError("initial error"))
        val chained = result
            .map {
                mapCalled = true
                it * 2
            }
            .peek {
                peekCalled = true
            }

        assertIs<Result.Failure<TestError>>(chained)
        assertEquals("initial error", chained.error.message)
        assertEquals(false, mapCalled)
        assertEquals(false, peekCalled)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "test", "hello world", "special chars: !@#$%^&*()"])
    fun `Success should handle various string values`(value: String) {
        val result = Result.Success(value)
        assertEquals(value, result.value)
        assertEquals(value, result.getOrNull())
    }

    @Test
    fun `Success and Failure should be sealed interface implementations`() {
        val success = Result.Success("test")
        val failure = Result.Failure(TestError("error"))

        // These checks are always true by design - testing the type system works correctly
        assertTrue(success is Result<*, *>)
        assertTrue(failure is Result<*, *>)
    }

    @Test
    fun `nested Result operations`() = runBlocking {
        val result = Result.Success(Result.Success("nested"))
        val flattened = result.flatMap { innerResult ->
            innerResult.map { "unwrapped: $it" }
        }

        assertIs<Result.Success<String>>(flattened)
        assertEquals("unwrapped: nested", flattened.value)
    }

    @Test
    fun `type variance should work correctly`() {
        runBlocking {
            // Test that @UnsafeVariance allows the expected type variance
            val stringResult: Result<TestError, String> = Result.Success("test")
            val anyResult: Result<Any, String> = stringResult.mapError { it as Any }

            assertIs<Result<Any, String>>(anyResult)
        }
    }

    @Test
    fun `complex error recovery scenario`() = runBlocking {
        val result: Result<TestError, String> = Result.Failure(TestError("database error"))
        val recovered = result
            .recover { error ->
                if (error.message.contains("database")) {
                    Result.Success("fallback value")
                } else {
                    Result.Failure(TestError("unrecoverable: ${error.message}"))
                }
            }
            .map { it.uppercase() }

        assertIs<Result.Success<String>>(recovered)
        assertEquals("FALLBACK VALUE", recovered.value)
    }

    @Test
    fun `multiple error transformations`() = runBlocking {
        val result: Result<String, Int> = Result.Failure("error1")
        val transformed = result
            .mapError { "$it -> error2" }
            .mapError { "$it -> error3" }

        assertIs<Result.Failure<String>>(transformed)
        assertEquals("error1 -> error2 -> error3", transformed.error)
    }
}
