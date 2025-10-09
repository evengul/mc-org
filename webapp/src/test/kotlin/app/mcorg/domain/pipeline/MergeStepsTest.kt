package app.mcorg.domain.pipeline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import java.util.stream.Stream

class MergeStepsTest {

    // Test data classes
    data class TestError(val message: String)
    data class ValidationError(val field: String, val reason: String)

    companion object {
        @JvmStatic
        fun combineTestData(): Stream<Arguments> = Stream.of(
            Arguments.of("hello", 42, "hello" to 42),
            Arguments.of(null, "test", null to "test"),
            Arguments.of(listOf(1, 2, 3), true, listOf(1, 2, 3) to true),
            Arguments.of("", 0, "" to 0)
        )

        @JvmStatic
        fun tripleTestData(): Stream<Arguments> = Stream.of(
            Arguments.of("a", 1, true, Triple("a", 1, true)),
            Arguments.of(null, null, null, Triple(null, null, null)),
            Arguments.of("test", 42, listOf("item"), Triple("test", 42, listOf("item")))
        )
    }

    @Test
    fun `combine with two parameters should create Pair`() {
        val combiner = MergeSteps.combine<TestError, String, Int>()

        runBlocking {
            val result = combiner("hello", 42)
            assertIs<Result.Success<Pair<String, Int>>>(result)
            assertEquals(Pair("hello", 42), result.value)
        }
    }

    @Test
    fun `combine with three parameters should create Triple`() {
        val combiner = MergeSteps.combine<TestError, String, Int, Boolean>()

        runBlocking {
            val result = combiner("test", 123, true)
            assertIs<Result.Success<Triple<String, Int, Boolean>>>(result)
            assertEquals(Triple("test", 123, true), result.value)
        }
    }

    @ParameterizedTest
    @MethodSource("combineTestData")
    fun `combine should work with various data types`(first: Any?, second: Any?, expected: Pair<Any?, Any?>) {
        val combiner = MergeSteps.combine<TestError, Any?, Any?>()

        runBlocking {
            val result = combiner(first, second)
            assertIs<Result.Success<Pair<Any?, Any?>>>(result)
            assertEquals(expected, result.value)
        }
    }

    @ParameterizedTest
    @MethodSource("tripleTestData")
    fun `combine triple should work with various data types`(first: Any?, second: Any?, third: Any?, expected: Triple<Any?, Any?, Any?>) {
        val combiner = MergeSteps.combine<TestError, Any?, Any?, Any?>()

        runBlocking {
            val result = combiner(first, second, third)
            assertIs<Result.Success<Triple<Any?, Any?, Any?>>>(result)
            assertEquals(expected, result.value)
        }
    }

    @Test
    fun `collectList should convert map values to list`() {
        val collector = MergeSteps.collectList<TestError, String>()
        val inputMap = mapOf(
            "key1" to "value1",
            "key2" to "value2",
            "key3" to "value3"
        )

        runBlocking {
            val result = collector(inputMap)
            assertIs<Result.Success<List<String>>>(result)
            assertEquals(3, result.value.size)
            assertTrue(result.value.contains("value1"))
            assertTrue(result.value.contains("value2"))
            assertTrue(result.value.contains("value3"))
        }
    }

    @Test
    fun `collectList should handle empty map`() {
        val collector = MergeSteps.collectList<TestError, Any>()
        val emptyMap = emptyMap<String, Any>()

        runBlocking {
            val result = collector(emptyMap)
            assertIs<Result.Success<List<Any>>>(result)
            assertTrue(result.value.isEmpty())
        }
    }

    @Test
    fun `collectList should handle mixed types in map`() {
        val collector = MergeSteps.collectList<TestError, Any>()
        val mixedMap = mapOf<String, Any>(
            "string" to "hello",
            "number" to 42,
            "boolean" to true,
            "list" to listOf(1, 2, 3)
        )

        runBlocking {
            val result = collector(mixedMap)
            assertIs<Result.Success<List<Any>>>(result)
            assertEquals(4, result.value.size)
            assertTrue(result.value.contains("hello"))
            assertTrue(result.value.contains(42))
            assertTrue(result.value.contains(true))
            assertTrue(result.value.contains(listOf(1, 2, 3)))
        }
    }

    @Test
    fun `collectList with type casting should work correctly`() {
        val collector = MergeSteps.collectList<TestError, Int>()
        val numberMap = mapOf<String, Any>(
            "a" to 1,
            "b" to 2,
            "c" to 3
        )

        runBlocking {
            val result = collector(numberMap)
            assertIs<Result.Success<List<Int>>>(result)
            assertEquals(listOf(1, 2, 3), result.value.sorted()) // Sort to handle order variations
        }
    }

    @Test
    fun `transform with two parameters should apply transformation`() {
        val transformer = MergeSteps.transform<TestError, String, Int, String> { a, b ->
            "$a has length ${a.length} and number is $b"
        }

        runBlocking {
            val result = transformer("hello", 42)
            assertIs<Result.Success<String>>(result)
            assertEquals("hello has length 5 and number is 42", result.value)
        }
    }

    @Test
    fun `transform with three parameters should apply transformation`() {
        val transformer = MergeSteps.transform<TestError, Int, Int, Int, Int> { a, b, c ->
            a + b + c
        }

        runBlocking {
            val result = transformer(1, 2, 3)
            assertIs<Result.Success<Int>>(result)
            assertEquals(6, result.value)
        }
    }

    @Test
    fun `transform should handle complex transformations`() {
        val transformer = MergeSteps.transform<TestError, List<String>, String, Map<String, Int>> { list, prefix ->
            list.associateWith { "$prefix:${it.length}" }.mapValues { it.value.length }
        }

        runBlocking {
            val result = transformer(listOf("hello", "world"), "len")
            assertIs<Result.Success<Map<String, Int>>>(result)
            // "hello" -> "len:5" (length = 5), "world" -> "len:5" (length = 5)
            assertEquals(mapOf("hello" to 5, "world" to 5), result.value)
        }
    }

    @Test
    fun `transform should handle suspend operations`() {
        val transformer = MergeSteps.transform<TestError, String, Int, String> { text, delay ->
            kotlinx.coroutines.delay(delay.toLong())
            text.uppercase()
        }

        runBlocking {
            val result = transformer("hello", 10)
            assertIs<Result.Success<String>>(result)
            assertEquals("HELLO", result.value)
        }
    }

    @Test
    fun `transform should propagate exceptions from transformation function`() {
        val transformer = MergeSteps.transform<TestError, String, Int, String> { _, _ ->
            throw RuntimeException("Transform failed")
        }

        assertThrows<RuntimeException> {
            runBlocking {
                transformer("test", 42)
            }
        }
    }

    @Test
    fun `validate should return Success when predicate is true`() {
        val error = TestError("Validation failed")
        val validator = MergeSteps.validate(error) { a: String, b: Int ->
            a.length == b
        }

        runBlocking {
            val result = validator("hello", 5)
            assertIs<Result.Success<Pair<String, Int>>>(result)
            assertEquals(Pair("hello", 5), result.value)
        }
    }

    @Test
    fun `validate should return Failure when predicate is false`() {
        val error = TestError("Length mismatch")
        val validator = MergeSteps.validate(error) { a: String, b: Int ->
            a.length == b
        }

        runBlocking {
            val result = validator("hello", 3)
            assertIs<Result.Failure<TestError>>(result)
            assertEquals(error, result.error)
        }
    }

    @Test
    fun `validate should handle complex predicates`() {
        val error = ValidationError("range", "values must be within range")
        val validator = MergeSteps.validate(error) { min: Int, max: Int ->
            min >= 0 && max <= 100 && min < max
        }

        runBlocking {
            val validResult = validator(10, 20)
            assertIs<Result.Success<Pair<Int, Int>>>(validResult)
            assertEquals(Pair(10, 20), validResult.value)

            val invalidResult = validator(50, 30)
            assertIs<Result.Failure<ValidationError>>(invalidResult)
            assertEquals(error, invalidResult.error)
        }
    }

    @Test
    fun `validate should handle suspend predicates`() {
        val error = TestError("Async validation failed")
        val validator = MergeSteps.validate(error) { a: String, b: String ->
            kotlinx.coroutines.delay(1) // Simulate async operation
            a.contains(b)
        }

        runBlocking {
            val successResult = validator("hello world", "world")
            assertIs<Result.Success<Pair<String, String>>>(successResult)
            assertEquals(Pair("hello world", "world"), successResult.value)

            val failureResult = validator("hello world", "test")
            assertIs<Result.Failure<TestError>>(failureResult)
            assertEquals(error, failureResult.error)
        }
    }

    @Test
    fun `validate should propagate exceptions from predicate`() {
        val error = TestError("Validation error")
        val validator = MergeSteps.validate(error) { _: String, _: String ->
            throw RuntimeException("Predicate failed")
        }

        assertThrows<RuntimeException> {
            runBlocking {
                validator("test", "data")
            }
        }
    }

    @Test
    fun `MergeSteps methods should work together in pipeline`() {
        val combiner = MergeSteps.combine<TestError, String, Int>()
        val validator = MergeSteps.validate(TestError("Invalid pair")) { str: String, num: Int ->
            str.isNotEmpty() && num > 0
        }
        val transformer = MergeSteps.transform<TestError, String, Int, String> { str, num ->
            "$str: $num"
        }

        runBlocking {
            val combined = combiner("hello", 42)
            assertIs<Result.Success<Pair<String, Int>>>(combined)

            val validated = validator("hello", 42)
            assertIs<Result.Success<Pair<String, Int>>>(validated)

            val transformed = transformer("hello", 42)
            assertIs<Result.Success<String>>(transformed)
            assertEquals("hello: 42", transformed.value)
        }
    }

    @Test
    fun `MergeSteps should handle pipeline failure correctly`() {
        val combiner = MergeSteps.combine<TestError, String, Int>()
        val validator = MergeSteps.validate(TestError("Invalid data")) { str: String, num: Int ->
            str.isNotEmpty() && num > 0
        }

        runBlocking {
            val combined = combiner("", -1) // Invalid data
            assertIs<Result.Success<Pair<String, Int>>>(combined)

            val validated = validator("", -1) // This will fail validation
            assertIs<Result.Failure<TestError>>(validated)
            assertEquals("Invalid data", validated.error.message)
        }
    }

    @Test
    fun `collectList should maintain insertion order when possible`() {
        val collector = MergeSteps.collectList<TestError, String>()
        val orderedMap = linkedMapOf(
            "first" to "a",
            "second" to "b",
            "third" to "c"
        )

        runBlocking {
            val result = collector(orderedMap)
            assertIs<Result.Success<List<String>>>(result)
            assertEquals(listOf("a", "b", "c"), result.value)
        }
    }

    @Test
    fun `combine methods should handle null values correctly`() {
        val pairCombiner = MergeSteps.combine<TestError, String?, Int?>()
        val tripleCombiner = MergeSteps.combine<TestError, String?, Int?, Boolean?>()

        runBlocking {
            val pairResult = pairCombiner(null, null)
            assertIs<Result.Success<Pair<String?, Int?>>>(pairResult)
            assertEquals(Pair(null, null), pairResult.value)

            val tripleResult = tripleCombiner(null, 42, null)
            assertIs<Result.Success<Triple<String?, Int?, Boolean?>>>(tripleResult)
            assertEquals(Triple(null, 42, null), tripleResult.value)
        }
    }

    @Test
    fun `transform should work with nullable types`() {
        val transformer = MergeSteps.transform<TestError, String?, Int?, String> { a, b ->
            "a=${a ?: "null"}, b=${b ?: "null"}"
        }

        runBlocking {
            val result = transformer(null, 42)
            assertIs<Result.Success<String>>(result)
            assertEquals("a=null, b=42", result.value)
        }
    }

    @Test
    fun `validate should work with different error types`() {
        val stringError = "Simple error message"
        val validator = MergeSteps.validate(stringError) { a: Int, b: Int ->
            a + b == 10
        }

        runBlocking {
            val failureResult = validator(3, 4)
            assertIs<Result.Failure<String>>(failureResult)
            assertEquals(stringError, failureResult.error)
        }
    }
}
