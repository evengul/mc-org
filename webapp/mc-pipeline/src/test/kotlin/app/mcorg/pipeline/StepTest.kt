package app.mcorg.pipeline

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class StepTest {

    data class TestError(val message: String)

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

    // MCO-553: Step is a fun interface, so the anonymous-object ceremony is optional.

    @Test
    fun `a Step can be a lambda`() {
        val step = Step<String, TestError, Int> { input -> Result.success(input.length) }

        runBlocking {
            val result = step.process("hello")
            assertIs<Result.Success<Int>>(result)
            assertEquals(5, result.value)
        }
    }

    @Test
    fun `a lambda Step can suspend`() {
        val step = Step<Int, TestError, Int> { input ->
            delay(1)
            if (input % 2 == 0) Result.success(input) else Result.failure(TestError("odd"))
        }

        runBlocking {
            assertEquals(4, step.process(4).getOrNull())
            val odd = step.process(3)
            assertIs<Result.Failure<TestError>>(odd)
            assertEquals("odd", odd.error.message)
        }
    }

    @Test
    fun `Steps can be chained using Result operations`() {
        val parseStep = Step<String, TestError, Int> { input ->
            input.toIntOrNull()?.let { Result.success(it) }
                ?: Result.failure(TestError("Cannot parse '$input' as integer"))
        }
        val validateStep = Step<Int, TestError, Int> { n ->
            if (n > 0) Result.success(n) else Result.failure(TestError("Number must be positive"))
        }
        val doubleStep = Step<Int, TestError, Int> { n -> Result.success(n * 2) }

        runBlocking {
            val result = parseStep.process("42")
                .flatMap { validateStep.process(it) }
                .flatMap { doubleStep.process(it) }

            assertIs<Result.Success<Int>>(result)
            assertEquals(84, result.value)
        }
    }

    @Test
    fun `Step chaining should short-circuit on failure`() {
        val parseStep = Step<String, TestError, Int> { input ->
            input.toIntOrNull()?.let { Result.success(it) }
                ?: Result.failure(TestError("Cannot parse '$input' as integer"))
        }
        val validateStep = Step<Int, TestError, Int> { n ->
            if (n > 0) Result.success(n) else Result.failure(TestError("Number must be positive"))
        }
        var doubleStepCalled = false
        val doubleStep = Step<Int, TestError, Int> { n ->
            doubleStepCalled = true
            Result.success(n * 2)
        }

        runBlocking {
            val result = parseStep.process("-5")
                .flatMap { validateStep.process(it) }
                .flatMap { doubleStep.process(it) }

            assertIs<Result.Failure<TestError>>(result)
            assertEquals("Number must be positive", result.error.message)
            assertFalse(doubleStepCalled)
        }
    }

    @Test
    fun `multiple Steps can have different input and output types`() {
        val stringToIntStep = Step<String, TestError, Int> { Result.success(it.length) }
        val intToListStep = Step<Int, TestError, List<String>> { n -> Result.success((1..n).map { "item$it" }) }

        runBlocking {
            val stringResult: Result<TestError, Int> = stringToIntStep.process("hello")
            assertIs<Result.Success<Int>>(stringResult)
            assertEquals(5, stringResult.value)

            val listResult = intToListStep.process(stringResult.getOrNull()!!)
            assertIs<Result.Success<List<String>>>(listResult)
            assertEquals(listOf("item1", "item2", "item3", "item4", "item5"), listResult.value)
        }
    }

    @Test
    fun `Step generic constraints should work correctly`() {
        // Contravariant input (in I)
        val genericStep: Step<Any, TestError, String> = Step { Result.success("test") }
        val specificStep: Step<String, TestError, String> = genericStep

        // Covariant error (out E)
        val specificErrorStep: Step<String, TestError, String> = Step { Result.success(it) }
        val genericErrorStep: Step<String, Any, String> = specificErrorStep

        // Covariant success (out S)
        val stringStep: Step<String, TestError, String> = Step { Result.success(it) }
        val anyStep: Step<String, TestError, Any> = stringStep

        runBlocking {
            assertIs<Result.Success<String>>(specificStep.process("input"))
            assertIs<Result.Success<String>>(genericErrorStep.process("input"))
            assertIs<Result.Success<Any>>(anyStep.process("input"))
        }
    }

    @Test
    fun `Steps should work with null values`() {
        val nullStep = Step<Any, TestError, String?> { Result.success(null) }
        val nullValidationStep = Step<String?, TestError, String?> { input ->
            if (input != null) Result.success(input) else Result.failure(TestError("Cannot be null"))
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
