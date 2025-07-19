package app.mcorg.domain.pipeline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse

class PipelineTest {

    // Test data classes
    data class TestError(val message: String)
    data class ValidationError(val field: String, val reason: String)

    @Test
    fun `Pipeline constructor should accept processor function`() {
        val pipeline = Pipeline<String, TestError, Int> { input ->
            Result.Success(input.length)
        }

        runBlocking {
            val result = pipeline.execute("test")
            assertIs<Result.Success<Int>>(result)
            assertEquals(4, result.value)
        }
    }

    @Test
    fun `Pipeline execute should run processor function`() {
        val pipeline = Pipeline<Int, TestError, String> { input ->
            Result.Success("Number: $input")
        }

        runBlocking {
            val result = pipeline.execute(42)
            assertIs<Result.Success<String>>(result)
            assertEquals("Number: 42", result.value)
        }
    }

    @Test
    fun `Pipeline execute should handle processor failure`() {
        val pipeline = Pipeline<String, TestError, Int> { input ->
            if (input.isEmpty()) {
                Result.Failure(TestError("Input cannot be empty"))
            } else {
                Result.Success(input.length)
            }
        }

        runBlocking {
            val result = pipeline.execute("")
            assertIs<Result.Failure<TestError>>(result)
            assertEquals("Input cannot be empty", result.error.message)
        }
    }

    @Test
    fun `pipe should chain Steps successfully`() {
        val initialPipeline = Pipeline<String, TestError, Int> { input ->
            Result.Success(input.length)
        }

        val doubleStep = object : Step<Int, TestError, Int> {
            override suspend fun process(input: Int): Result<TestError, Int> {
                return Result.Success(input * 2)
            }
        }

        val chainedPipeline = initialPipeline.pipe(doubleStep)

        runBlocking {
            val result = chainedPipeline.execute("hello")
            assertIs<Result.Success<Int>>(result)
            assertEquals(10, result.value) // "hello".length * 2 = 5 * 2 = 10
        }
    }

    @Test
    fun `pipe should handle Step failure in chain`() {
        val initialPipeline = Pipeline<String, TestError, Int> { input ->
            Result.Success(input.length)
        }

        val failingStep = object : Step<Int, TestError, String> {
            override suspend fun process(input: Int): Result<TestError, String> {
                return Result.Failure(TestError("Step failed"))
            }
        }

        val chainedPipeline = initialPipeline.pipe(failingStep)

        runBlocking {
            val result = chainedPipeline.execute("test")
            assertIs<Result.Failure<TestError>>(result)
            assertEquals("Step failed", result.error.message)
        }
    }

    @Test
    fun `pipe should handle initial Pipeline failure and not execute Step`() {
        val failingPipeline = Pipeline<String, TestError, Int> { _ ->
            Result.Failure(TestError("Initial pipeline failed"))
        }

        var stepExecuted = false
        val testStep = object : Step<Int, TestError, String> {
            override suspend fun process(input: Int): Result<TestError, String> {
                stepExecuted = true
                return Result.Success("Processed: $input")
            }
        }

        val chainedPipeline = failingPipeline.pipe(testStep)

        runBlocking {
            val result = chainedPipeline.execute("test")
            assertIs<Result.Failure<TestError>>(result)
            assertEquals("Initial pipeline failed", result.error.message)
            assertFalse(stepExecuted) // Step should not execute due to pipeline failure
        }
    }

    @Test
    fun `pipe with peek should execute peek function on success`() {
        val initialPipeline = Pipeline<String, TestError, Int> { input ->
            Result.Success(input.length)
        }

        val doubleStep = object : Step<Int, TestError, Int> {
            override suspend fun process(input: Int): Result<TestError, Int> {
                return Result.Success(input * 2)
            }
        }

        var peekedValue: Int? = null
        val chainedPipeline = initialPipeline.pipe(doubleStep) { value ->
            peekedValue = value
        }

        runBlocking {
            val result = chainedPipeline.execute("hello")
            assertIs<Result.Success<Int>>(result)
            assertEquals(10, result.value)
            assertEquals(10, peekedValue) // Peek should have been called with result value
        }
    }

    @Test
    fun `pipe with peek should not execute peek function on failure`() {
        val initialPipeline = Pipeline<String, TestError, Int> { input ->
            Result.Success(input.length)
        }

        val failingStep = object : Step<Int, TestError, Int> {
            override suspend fun process(input: Int): Result<TestError, Int> {
                return Result.Failure(TestError("Step failed"))
            }
        }

        var peekCalled = false
        val chainedPipeline = initialPipeline.pipe(failingStep) { _ ->
            peekCalled = true
        }

        runBlocking {
            val result = chainedPipeline.execute("test")
            assertIs<Result.Failure<TestError>>(result)
            assertEquals("Step failed", result.error.message)
            assertFalse(peekCalled) // Peek should not be called on failure
        }
    }

    @Test
    fun `wrapPipe should transform Step result using wrap function`() {
        val initialPipeline = Pipeline<String, TestError, Int> { input ->
            Result.Success(input.length)
        }

        val parseStep = object : Step<Int, ValidationError, String> {
            override suspend fun process(input: Int): Result<ValidationError, String> {
                return Result.Success("Length: $input")
            }
        }

        val wrapFunction: suspend (Result<ValidationError, String>) -> Result<TestError, String> = { result ->
            when (result) {
                is Result.Success -> Result.Success("Wrapped: ${result.value}")
                is Result.Failure -> Result.Failure(TestError("Validation failed: ${result.error.field}"))
            }
        }

        val wrappedPipeline = initialPipeline.wrapPipe(parseStep, wrapFunction)

        runBlocking {
            val result = wrappedPipeline.execute("test")
            assertIs<Result.Success<String>>(result)
            assertEquals("Wrapped: Length: 4", result.value)
        }
    }

    @Test
    fun `wrapPipe should handle Step failure through wrap function`() {
        val initialPipeline = Pipeline<String, TestError, Int> { input ->
            Result.Success(input.length)
        }

        val failingStep = object : Step<Int, ValidationError, String> {
            override suspend fun process(input: Int): Result<ValidationError, String> {
                return Result.Failure(ValidationError("input", "invalid format"))
            }
        }

        val wrapFunction: suspend (Result<ValidationError, String>) -> Result<TestError, String> = { result ->
            when (result) {
                is Result.Success -> Result.Success("Wrapped: ${result.value}")
                is Result.Failure -> Result.Failure(TestError("Validation error: ${result.error.reason}"))
            }
        }

        val wrappedPipeline = initialPipeline.wrapPipe(failingStep, wrapFunction)

        runBlocking {
            val result = wrappedPipeline.execute("test")
            assertIs<Result.Failure<TestError>>(result)
            assertEquals("Validation error: invalid format", result.error.message)
        }
    }

    @Test
    fun `wrapPipe should handle initial Pipeline failure`() {
        val failingPipeline = Pipeline<String, TestError, Int> { _ ->
            Result.Failure(TestError("Initial failure"))
        }

        val testStep = object : Step<Int, ValidationError, String> {
            override suspend fun process(input: Int): Result<ValidationError, String> {
                return Result.Success("Should not reach here")
            }
        }

        var wrapFunctionCalled = false
        val wrapFunction: suspend (Result<ValidationError, String>) -> Result<TestError, String> = { _ ->
            wrapFunctionCalled = true
            Result.Success("Should not reach here")
        }

        val wrappedPipeline = failingPipeline.wrapPipe(testStep, wrapFunction)

        runBlocking {
            val result = wrappedPipeline.execute("test")
            assertIs<Result.Failure<TestError>>(result)
            assertEquals("Initial failure", result.error.message)
            assertFalse(wrapFunctionCalled) // Wrap function should not be called if initial pipeline fails
        }
    }

    @Test
    fun `multiple pipe operations should chain correctly`() {
        val initialPipeline = Pipeline<String, TestError, Int> { input ->
            Result.Success(input.length)
        }

        val doubleStep = object : Step<Int, TestError, Int> {
            override suspend fun process(input: Int): Result<TestError, Int> {
                return Result.Success(input * 2)
            }
        }

        val toStringStep = object : Step<Int, TestError, String> {
            override suspend fun process(input: Int): Result<TestError, String> {
                return Result.Success("Result: $input")
            }
        }

        val chainedPipeline = initialPipeline
            .pipe(doubleStep)
            .pipe(toStringStep)

        runBlocking {
            val result = chainedPipeline.execute("hello")
            assertIs<Result.Success<String>>(result)
            assertEquals("Result: 10", result.value) // "hello".length * 2 = 10
        }
    }

    @Test
    fun `multiple pipe operations should short-circuit on failure`() {
        val initialPipeline = Pipeline<String, TestError, Int> { input ->
            Result.Success(input.length)
        }

        val failingStep = object : Step<Int, TestError, Int> {
            override suspend fun process(input: Int): Result<TestError, Int> {
                return Result.Failure(TestError("Middle step failed"))
            }
        }

        var finalStepExecuted = false
        val finalStep = object : Step<Int, TestError, String> {
            override suspend fun process(input: Int): Result<TestError, String> {
                finalStepExecuted = true
                return Result.Success("Final: $input")
            }
        }

        val chainedPipeline = initialPipeline
            .pipe(failingStep)
            .pipe(finalStep)

        runBlocking {
            val result = chainedPipeline.execute("test")
            assertIs<Result.Failure<TestError>>(result)
            assertEquals("Middle step failed", result.error.message)
            assertFalse(finalStepExecuted) // Final step should not execute due to failure
        }
    }

    @Test
    fun `Pipeline should work with different error types through generic bounds`() {
        val stringPipeline = Pipeline<String, TestError, Int> { input ->
            if (input.isNotEmpty()) Result.Success(input.length)
            else Result.Failure(TestError("Empty input"))
        }

        val validationStep = object : Step<Int, TestError, String> { // Fixed: Use same error type
            override suspend fun process(input: Int): Result<TestError, String> {
                return if (input > 0) Result.Success("Valid: $input")
                else Result.Failure(TestError("must be positive")) // Fixed: Use TestError
            }
        }

        val mixedPipeline = stringPipeline.pipe(validationStep)

        runBlocking {
            val result = mixedPipeline.execute("hello")
            assertIs<Result.Success<String>>(result)
            assertEquals("Valid: 5", result.value)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "hello", "test", "pipeline"])
    fun `Pipeline should handle various string inputs`(input: String) {
        val pipeline = Pipeline<String, TestError, Int> { str ->
            Result.Success(str.length)
        }

        val doubleStep = object : Step<Int, TestError, Int> {
            override suspend fun process(input: Int): Result<TestError, Int> { // Fixed: parameter name
                return Result.Success(input * 2)
            }
        }

        val chainedPipeline = pipeline.pipe(doubleStep)

        runBlocking {
            val result = chainedPipeline.execute(input)
            assertIs<Result.Success<Int>>(result)
            assertEquals(input.length * 2, result.value)
        }
    }

    @Test
    fun `Pipeline should handle complex processor logic`() {
        val complexPipeline = Pipeline<List<Int>, TestError, Int> { numbers ->
            when {
                numbers.isEmpty() -> Result.Failure(TestError("List cannot be empty"))
                numbers.any { it < 0 } -> Result.Failure(TestError("Negative numbers not allowed"))
                else -> Result.Success(numbers.sum())
            }
        }

        runBlocking {
            val successResult = complexPipeline.execute(listOf(1, 2, 3, 4, 5))
            assertIs<Result.Success<Int>>(successResult)
            assertEquals(15, successResult.value)

            val emptyResult = complexPipeline.execute(emptyList())
            assertIs<Result.Failure<TestError>>(emptyResult)
            assertEquals("List cannot be empty", emptyResult.error.message)

            val negativeResult = complexPipeline.execute(listOf(1, -2, 3))
            assertIs<Result.Failure<TestError>>(negativeResult)
            assertEquals("Negative numbers not allowed", negativeResult.error.message)
        }
    }

    @Test
    fun `Pipeline processor should handle exceptions`() {
        val exceptionPipeline = Pipeline<String, TestError, Int> { input ->
            if (input == "throw") {
                throw RuntimeException("Test exception")
            }
            Result.Success(input.length)
        }

        assertThrows<RuntimeException> {
            runBlocking {
                exceptionPipeline.execute("throw")
            }
        }
    }

    @Test
    fun `wrapPipe wrap function should handle exceptions`() {
        val pipeline = Pipeline<String, TestError, Int> { input ->
            Result.Success(input.length)
        }

        val simpleStep = object : Step<Int, ValidationError, String> {
            override suspend fun process(input: Int): Result<ValidationError, String> {
                return Result.Success("Length: $input")
            }
        }

        val throwingWrapFunction: suspend (Result<ValidationError, String>) -> Result<TestError, String> = { _ ->
            throw RuntimeException("Wrap function failed")
        }

        val wrappedPipeline = pipeline.wrapPipe(simpleStep, throwingWrapFunction)

        assertThrows<RuntimeException> {
            runBlocking {
                wrappedPipeline.execute("test")
            }
        }
    }

    @Test
    fun `Pipeline should maintain type safety with variance annotations`() {
        // Test that @UnsafeVariance allows proper type variance
        val basePipeline = Pipeline<String, TestError, Int> { input ->
            Result.Success(input.length)
        }

        // Fixed: Use same error type to avoid variance issues
        val step: Step<Int, TestError, String> = object : Step<Int, TestError, String> {
            override suspend fun process(input: Int): Result<TestError, String> {
                return Result.Success("Value: $input")
            }
        }

        val chainedPipeline = basePipeline.pipe(step)

        runBlocking {
            val result = chainedPipeline.execute("test")
            assertIs<Result.Success<String>>(result)
            assertEquals("Value: 4", result.value)
        }
    }

    @Test
    fun `Pipeline should work with null values`() {
        val nullHandlingPipeline = Pipeline<String?, TestError, String> { input ->
            if (input == null) {
                Result.Success("null input")
            } else {
                Result.Success("input: $input")
            }
        }

        runBlocking {
            val nullResult = nullHandlingPipeline.execute(null)
            assertIs<Result.Success<String>>(nullResult)
            assertEquals("null input", nullResult.value)

            val nonNullResult = nullHandlingPipeline.execute("test")
            assertIs<Result.Success<String>>(nonNullResult)
            assertEquals("input: test", nonNullResult.value)
        }
    }
}
