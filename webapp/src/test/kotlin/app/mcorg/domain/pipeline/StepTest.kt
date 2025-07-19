package app.mcorg.domain.pipeline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class StepTest {

    // Test data classes
    data class TestError(val message: String)
    data class ValidationError(val field: String, val reason: String)

    @Test
    fun `Step interface should define process method`() {
        val step = object : Step<String, TestError, Int> {
            override suspend fun process(input: String): Result<TestError, Int> {
                return Result.Success(input.length)
            }
        }

        runBlocking {
            val result = step.process("test")
            assertIs<Result.Success<Int>>(result)
            assertEquals(4, result.value)
        }
    }

    @Test
    fun `Step interface should handle failure cases`() {
        val step = object : Step<String, TestError, Int> {
            override suspend fun process(input: String): Result<TestError, Int> {
                return if (input.isEmpty()) {
                    Result.Failure(TestError("Input cannot be empty"))
                } else {
                    Result.Success(input.length)
                }
            }
        }

        runBlocking {
            val result = step.process("")
            assertIs<Result.Failure<TestError>>(result)
            assertEquals("Input cannot be empty", result.error.message)
        }
    }

    @Test
    fun `value factory method should create Step that returns constant value`() {
        val constantValue = "Hello, World!"
        val step = Step.value<TestError, String>(constantValue)

        runBlocking {
            val result = step.process(42) // Input can be anything
            assertIs<Result.Success<String>>(result)
            assertEquals(constantValue, result.value)
        }
    }

    @Test
    fun `value factory method should work with different value types`() {
        val intStep = Step.value<TestError, Int>(42)
        val listStep = Step.value<TestError, List<String>>(listOf("a", "b", "c"))
        val boolStep = Step.value<TestError, Boolean>(true)

        runBlocking {
            val intResult = intStep.process("ignored")
            assertIs<Result.Success<Int>>(intResult)
            assertEquals(42, intResult.value)

            val listResult = listStep.process("not null") // Fixed: use non-null value
            assertIs<Result.Success<List<String>>>(listResult)
            assertEquals(listOf("a", "b", "c"), listResult.value)

            val boolResult = boolStep.process(100)
            assertIs<Result.Success<Boolean>>(boolResult)
            assertTrue(boolResult.value)
        }
    }

    @Test
    fun `value factory method should accept Any input type`() {
        val step = Step.value<TestError, String>("test")

        runBlocking {
            // Should work with any input type
            assertEquals("test", step.process("string input").getOrNull())
            assertEquals("test", step.process(123).getOrNull())
            assertEquals("test", step.process(listOf(1, 2, 3)).getOrNull())
            assertEquals("test", step.process("non-null").getOrNull()) // Fixed: use non-null value
        }
    }

    @Test
    fun `validate factory method should return Success when predicate is true`() {
        val error = ValidationError("age", "must be positive")
        val step = Step.validate(error) { age: Int -> age > 0 }

        runBlocking {
            val result = step.process(25)
            assertIs<Result.Success<Int>>(result)
            assertEquals(25, result.value)
        }
    }

    @Test
    fun `validate factory method should return Failure when predicate is false`() {
        val error = ValidationError("age", "must be positive")
        val step = Step.validate(error) { age: Int -> age > 0 }

        runBlocking {
            val result = step.process(-5)
            assertIs<Result.Failure<ValidationError>>(result)
            assertEquals(error, result.error)
        }
    }

    @Test
    fun `validate factory method should work with string validation`() {
        val error = TestError("String cannot be empty")
        val step = Step.validate(error) { text: String -> text.isNotEmpty() }

        runBlocking {
            val validResult = step.process("Hello")
            assertIs<Result.Success<String>>(validResult)
            assertEquals("Hello", validResult.value)

            val invalidResult = step.process("")
            assertIs<Result.Failure<TestError>>(invalidResult)
            assertEquals("String cannot be empty", invalidResult.error.message)
        }
    }

    @Test
    fun `validate factory method should work with complex predicates`() {
        val error = TestError("Email is invalid")
        val step = Step.validate(error) { email: String ->
            email.contains("@") && email.contains(".")
        }

        runBlocking {
            val validResult = step.process("user@example.com")
            assertIs<Result.Success<String>>(validResult)
            assertEquals("user@example.com", validResult.value)

            val invalidResult = step.process("invalid-email")
            assertIs<Result.Failure<TestError>>(invalidResult)
            assertEquals("Email is invalid", invalidResult.error.message)
        }
    }

    @Test
    fun `validate factory method should handle suspend predicates`() {
        val error = TestError("Async validation failed")
        val step = Step.validate(error) { value: Int ->
            // Simulate async operation
            kotlinx.coroutines.delay(1)
            value % 2 == 0 // Only even numbers are valid
        }

        runBlocking {
            val validResult = step.process(4)
            assertIs<Result.Success<Int>>(validResult)
            assertEquals(4, validResult.value)

            val invalidResult = step.process(3)
            assertIs<Result.Failure<TestError>>(invalidResult)
            assertEquals("Async validation failed", invalidResult.error.message)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 10, 100, 1000])
    fun `validate should work with various valid positive numbers`(number: Int) {
        val error = TestError("Number must be positive")
        val step = Step.validate(error) { n: Int -> n > 0 }

        runBlocking {
            val result = step.process(number)
            assertIs<Result.Success<Int>>(result)
            assertEquals(number, result.value)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, -2, -10, 0])
    fun `validate should fail with non-positive numbers`(number: Int) {
        val error = TestError("Number must be positive")
        val step = Step.validate(error) { n: Int -> n > 0 }

        runBlocking {
            val result = step.process(number)
            assertIs<Result.Failure<TestError>>(result)
            assertEquals("Number must be positive", result.error.message)
        }
    }

    @Test
    fun `Steps can be chained using Result operations`() {
        val parseStep = object : Step<String, TestError, Int> {
            override suspend fun process(input: String): Result<TestError, Int> {
                return try {
                    Result.Success(input.toInt())
                } catch (_: NumberFormatException) { // Fixed: use underscore for unused parameter
                    Result.Failure(TestError("Cannot parse '$input' as integer"))
                }
            }
        }

        val validateStep = Step.validate(TestError("Number must be positive")) { n: Int -> n > 0 }
        val doubleStep = object : Step<Int, TestError, Int> {
            override suspend fun process(input: Int): Result<TestError, Int> {
                return Result.Success(input * 2)
            }
        }

        runBlocking {
            val input = "42"

            val result = parseStep.process(input)
                .flatMap { validateStep.process(it) }
                .flatMap { doubleStep.process(it) }

            assertIs<Result.Success<Int>>(result)
            assertEquals(84, result.value)
        }
    }

    @Test
    fun `Step chaining should short-circuit on failure`() {
        val parseStep = object : Step<String, TestError, Int> {
            override suspend fun process(input: String): Result<TestError, Int> {
                return try {
                    Result.Success(input.toInt())
                } catch (_: NumberFormatException) { // Fixed: use underscore for unused parameter
                    Result.Failure(TestError("Cannot parse '$input' as integer"))
                }
            }
        }

        val validateStep = Step.validate(TestError("Number must be positive")) { n: Int -> n > 0 }
        var doubleStepCalled = false
        val doubleStep = object : Step<Int, TestError, Int> {
            override suspend fun process(input: Int): Result<TestError, Int> {
                doubleStepCalled = true
                return Result.Success(input * 2)
            }
        }

        runBlocking {
            val input = "-5"

            val result = parseStep.process(input)
                .flatMap { validateStep.process(it) }
                .flatMap { doubleStep.process(it) }

            assertIs<Result.Failure<TestError>>(result)
            assertEquals("Number must be positive", result.error.message)
            assertFalse(doubleStepCalled) // Should not be called due to short-circuiting
        }
    }

    @Test
    fun `validate predicate exceptions should be propagated`() {
        val error = TestError("Validation error")
        val step = Step.validate(error) { _: String ->
            throw RuntimeException("Predicate failed")
        }

        assertThrows<RuntimeException> {
            runBlocking {
                step.process("test")
            }
        }
    }

    @Test
    fun `multiple Steps can have different input and output types`() {
        val stringToIntStep = object : Step<String, TestError, Int> {
            override suspend fun process(input: String): Result<TestError, Int> {
                return Result.Success(input.length)
            }
        }

        val intToListStep = object : Step<Int, TestError, List<String>> {
            override suspend fun process(input: Int): Result<TestError, List<String>> {
                return Result.Success((1..input).map { "item$it" })
            }
        }

        runBlocking {
            // Declare the initial result with explicit error type
            val stringResult: Result<TestError, Int> = stringToIntStep.process("hello")
            assertIs<Result.Success<Int>>(stringResult)
            assertEquals(5, stringResult.value)

            // Process the chaining step by step to avoid type inference issues
            val intValue = stringResult.getOrNull()!!
            val listResult = intToListStep.process(intValue)
            assertIs<Result.Success<List<String>>>(listResult)
            assertEquals(listOf("item1", "item2", "item3", "item4", "item5"), listResult.value)
        }
    }

    @Test
    fun `Step generic constraints should work correctly`() {
        // Test contravariance of input type (in I)
        val genericStep: Step<Any, TestError, String> = Step.value("test")
        val specificStep: Step<String, TestError, String> = genericStep // Should compile

        // Test covariance of error type (out E)
        val specificErrorStep: Step<String, TestError, String> = Step.validate(TestError("error")) { true }
        val genericErrorStep: Step<String, Any, String> = specificErrorStep // Should compile

        // Test covariance of success type (out S)
        val stringStep: Step<String, TestError, String> = Step.value("test")
        val anyStep: Step<String, TestError, Any> = stringStep // Should compile

        runBlocking {
            val result1 = specificStep.process("input")
            val result2 = genericErrorStep.process("input")
            val result3 = anyStep.process("input")

            assertIs<Result.Success<String>>(result1)
            assertIs<Result.Success<String>>(result2)
            assertIs<Result.Success<Any>>(result3)
        }
    }

    @Test
    fun `Steps should work with null values`() {
        val nullStep = Step.value<TestError, String?>(null)
        val nullValidationStep = Step.validate(TestError("Cannot be null")) { input: String? ->
            input != null
        }

        runBlocking {
            val nullResult = nullStep.process("anything")
            assertIs<Result.Success<String?>>(nullResult)
            assertEquals(null, nullResult.value)

            val validationResult = nullValidationStep.process(null)
            assertIs<Result.Failure<TestError>>(validationResult)
            assertEquals("Cannot be null", validationResult.error.message)
        }
    }
}
