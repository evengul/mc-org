package app.mcorg.domain.pipeline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PipelineScopeTest {

    data class TestError(val message: String)

    // region bind() tests

    @Test
    fun `bind on Success should return the value`() = runBlocking {
        var result: String? = null

        pipeline<TestError, String>(
            onSuccess = { result = it },
            onFailure = { }
        ) {
            Result.success<TestError, String>("hello").bind()
        }

        assertEquals("hello", result)
    }

    @Test
    fun `bind on Failure should short-circuit the pipeline`() = runBlocking {
        var successCalled = false
        var failureError: TestError? = null

        pipeline<TestError, String>(
            onSuccess = { successCalled = true },
            onFailure = { failureError = it }
        ) {
            Result.failure<TestError>(TestError("something went wrong")).bind()
            "this should never be reached"
        }

        assertFalse(successCalled)
        assertEquals("something went wrong", failureError?.message)
    }

    @Test
    fun `bind should short-circuit at first failure in a chain`() = runBlocking {
        var step2Executed = false
        var failureError: TestError? = null

        pipeline<TestError, Int>(
            onSuccess = { },
            onFailure = { failureError = it }
        ) {
            val a = Result.success<TestError, Int>(5).bind()
            Result.failure<TestError>(TestError("step 1 failed")).bind<Int>()
            step2Executed = true
            a + 10
        }

        assertFalse(step2Executed)
        assertEquals("step 1 failed", failureError?.message)
    }

    @Test
    fun `bind should allow chaining multiple successful results`() = runBlocking {
        var result: String? = null

        pipeline<TestError, String>(
            onSuccess = { result = it },
            onFailure = { }
        ) {
            val a = Result.success<TestError, Int>(5).bind()
            val b = Result.success<TestError, Int>(10).bind()
            val c = Result.success<TestError, String>("sum").bind()
            "$c = ${a + b}"
        }

        assertEquals("sum = 15", result)
    }

    // endregion

    // region Step.run() tests

    @Test
    fun `Step run should process input and return success value`() = runBlocking {
        val lengthStep = object : Step<String, TestError, Int> {
            override suspend fun process(input: String): Result<TestError, Int> {
                return Result.success(input.length)
            }
        }

        var result: Int? = null

        pipeline<TestError, Int>(
            onSuccess = { result = it },
            onFailure = { }
        ) {
            lengthStep.run("hello")
        }

        assertEquals(5, result)
    }

    @Test
    fun `Step run should short-circuit on failure`() = runBlocking {
        val failingStep = object : Step<String, TestError, Int> {
            override suspend fun process(input: String): Result<TestError, Int> {
                return Result.failure(TestError("step failed"))
            }
        }

        var failureError: TestError? = null

        pipeline<TestError, Int>(
            onSuccess = { },
            onFailure = { failureError = it }
        ) {
            failingStep.run("hello")
        }

        assertEquals("step failed", failureError?.message)
    }

    @Test
    fun `chaining multiple Steps with run`() = runBlocking {
        val parseStep = object : Step<String, TestError, Int> {
            override suspend fun process(input: String): Result<TestError, Int> {
                return try {
                    Result.success(input.toInt())
                } catch (e: NumberFormatException) {
                    Result.failure(TestError("Not a number"))
                }
            }
        }

        val doubleStep = object : Step<Int, TestError, Int> {
            override suspend fun process(input: Int): Result<TestError, Int> {
                return Result.success(input * 2)
            }
        }

        var result: String? = null

        pipeline<TestError, String>(
            onSuccess = { result = it },
            onFailure = { }
        ) {
            val number = parseStep.run("42")
            val doubled = doubleStep.run(number)
            "Result: $doubled"
        }

        assertEquals("Result: 84", result)
    }

    // endregion

    // region parallel() tests

    @Test
    fun `parallel should run two operations and return both results`() = runBlocking {
        var result: Pair<Int, String>? = null

        pipeline<TestError, Pair<Int, String>>(
            onSuccess = { result = it },
            onFailure = { }
        ) {
            parallel(
                { Result.success<TestError, Int>(42).bind() },
                { Result.success<TestError, String>("hello").bind() }
            )
        }

        assertEquals(42, result?.first)
        assertEquals("hello", result?.second)
    }

    @Test
    fun `parallel should short-circuit when first operation fails`() = runBlocking {
        var failureError: TestError? = null

        pipeline<TestError, Pair<Int, String>>(
            onSuccess = { },
            onFailure = { failureError = it }
        ) {
            parallel(
                { Result.failure<TestError>(TestError("first failed")).bind<Int>() },
                { Result.success<TestError, String>("hello").bind() }
            )
        }

        assertEquals("first failed", failureError?.message)
    }

    @Test
    fun `parallel with three operations`() = runBlocking {
        var result: Triple<Int, String, Boolean>? = null

        pipeline<TestError, Triple<Int, String, Boolean>>(
            onSuccess = { result = it },
            onFailure = { }
        ) {
            parallel(
                { Result.success<TestError, Int>(1).bind() },
                { Result.success<TestError, String>("two").bind() },
                { Result.success<TestError, Boolean>(true).bind() }
            )
        }

        assertEquals(Triple(1, "two", true), result)
    }

    @Test
    fun `parallel with four operations`() = runBlocking {
        var result: Quadruple<Int, String, Boolean, Double>? = null

        pipeline<TestError, Quadruple<Int, String, Boolean, Double>>(
            onSuccess = { result = it },
            onFailure = { }
        ) {
            parallel(
                { Result.success<TestError, Int>(1).bind() },
                { Result.success<TestError, String>("two").bind() },
                { Result.success<TestError, Boolean>(true).bind() },
                { Result.success<TestError, Double>(4.0).bind() }
            )
        }

        assertEquals(Quadruple(1, "two", true, 4.0), result)
    }

    @Test
    fun `parallel operations can use Steps`() = runBlocking {
        val step1 = object : Step<Int, TestError, String> {
            override suspend fun process(input: Int): Result<TestError, String> {
                return Result.success("Number: $input")
            }
        }

        val step2 = object : Step<String, TestError, Int> {
            override suspend fun process(input: String): Result<TestError, Int> {
                return Result.success(input.length)
            }
        }

        var result: Pair<String, Int>? = null

        pipeline<TestError, Pair<String, Int>>(
            onSuccess = { result = it },
            onFailure = { }
        ) {
            parallel(
                { step1.run(42) },
                { step2.run("hello world") }
            )
        }

        assertEquals("Number: 42", result?.first)
        assertEquals(11, result?.second)
    }

    // endregion

    // region pipelineResult() tests

    @Test
    fun `pipelineResult should return Success when pipeline completes`() = runBlocking {
        val result = pipelineResult<TestError, Int> {
            val a = Result.success<TestError, Int>(5).bind()
            val b = Result.success<TestError, Int>(10).bind()
            a + b
        }

        assertIs<Result.Success<Int>>(result)
        assertEquals(15, result.value)
    }

    @Test
    fun `pipelineResult should return Failure when pipeline short-circuits`() = runBlocking {
        val result = pipelineResult<TestError, Int> {
            Result.success<TestError, Int>(5).bind()
            Result.failure<TestError>(TestError("oops")).bind<Int>()
            999 // never reached
        }

        assertIs<Result.Failure<TestError>>(result)
        assertEquals("oops", result.error.message)
    }

    // endregion

    // region Complex scenario tests

    @Test
    fun `real-world scenario - validate then create then fetch`() = runBlocking {
        data class CreateInput(val name: String, val description: String)
        data class Created(val id: Int, val name: String, val description: String)

        val validateStep = object : Step<Map<String, String>, TestError, CreateInput> {
            override suspend fun process(input: Map<String, String>): Result<TestError, CreateInput> {
                val name = input["name"] ?: return Result.failure(TestError("name is required"))
                if (name.length < 3) return Result.failure(TestError("name too short"))
                val description = input["description"] ?: ""
                return Result.success(CreateInput(name, description))
            }
        }

        val createStep = object : Step<CreateInput, TestError, Int> {
            override suspend fun process(input: CreateInput): Result<TestError, Int> {
                return Result.success(42) // simulated ID
            }
        }

        val fetchStep = object : Step<Int, TestError, Created> {
            override suspend fun process(input: Int): Result<TestError, Created> {
                return Result.success(Created(input, "My Project", "A description"))
            }
        }

        var result: Created? = null

        pipeline<TestError, Created>(
            onSuccess = { result = it },
            onFailure = { }
        ) {
            val input = validateStep.run(mapOf("name" to "My Project", "description" to "A description"))
            val id = createStep.run(input)
            fetchStep.run(id)
        }

        assertEquals(42, result?.id)
        assertEquals("My Project", result?.name)
    }

    @Test
    fun `real-world scenario - parallel fetch with sequential follow-up`() = runBlocking {
        val fetchUser = object : Step<Int, TestError, String> {
            override suspend fun process(input: Int): Result<TestError, String> {
                return Result.success("User-$input")
            }
        }

        val fetchProjects = object : Step<Int, TestError, List<String>> {
            override suspend fun process(input: Int): Result<TestError, List<String>> {
                return Result.success(listOf("Project A", "Project B"))
            }
        }

        var result: String? = null

        pipeline<TestError, String>(
            onSuccess = { result = it },
            onFailure = { }
        ) {
            val (user, projects) = parallel(
                { fetchUser.run(1) },
                { fetchProjects.run(1) }
            )
            "$user has ${projects.size} projects"
        }

        assertEquals("User-1 has 2 projects", result)
    }

    @Test
    fun `accessing intermediate values in later steps`() = runBlocking {
        // This is the key advantage over the linear PipelineBuilder:
        // intermediate values can be reused freely.
        val fetchWorld = object : Step<Int, TestError, String> {
            override suspend fun process(input: Int): Result<TestError, String> {
                return Result.success("World-$input")
            }
        }

        val fetchMember = object : Step<Int, TestError, String> {
            override suspend fun process(input: Int): Result<TestError, String> {
                return Result.success("Member-$input")
            }
        }

        var result: String? = null

        pipeline<TestError, String>(
            onSuccess = { result = it },
            onFailure = { }
        ) {
            val world = fetchWorld.run(1)
            val member = fetchMember.run(2)
            // Can use both world and member here — impossible with linear PipelineBuilder
            "$world owned by $member"
        }

        assertEquals("World-1 owned by Member-2", result)
    }

    @Test
    fun `validation failure should stop pipeline before side effects`() = runBlocking {
        var sideEffectExecuted = false

        val validateStep = object : Step<String, TestError, String> {
            override suspend fun process(input: String): Result<TestError, String> {
                return if (input.length >= 3) Result.success(input)
                else Result.failure(TestError("Too short"))
            }
        }

        val sideEffectStep = object : Step<String, TestError, String> {
            override suspend fun process(input: String): Result<TestError, String> {
                sideEffectExecuted = true
                return Result.success("created: $input")
            }
        }

        var failureError: TestError? = null

        pipeline<TestError, String>(
            onSuccess = { },
            onFailure = { failureError = it }
        ) {
            val validated = validateStep.run("ab") // too short
            sideEffectStep.run(validated) // should not execute
        }

        assertFalse(sideEffectExecuted)
        assertEquals("Too short", failureError?.message)
    }

    // endregion

    // region getOrElse tests

    @Test
    fun `getOrElse should return value on Success`() {
        val result: Result<TestError, String> = Result.success("hello")
        val value = result.getOrElse { "default" }
        assertEquals("hello", value)
    }

    @Test
    fun `getOrElse should return fallback on Failure`() {
        val result: Result<TestError, String> = Result.failure(TestError("error"))
        val value = result.getOrElse { "default" }
        assertEquals("default", value)
    }

    @Test
    fun `getOrElse receives the error in the lambda`() {
        val result: Result<TestError, String> = Result.failure(TestError("specific error"))
        val value = result.getOrElse { error -> "Failed: ${error.message}" }
        assertEquals("Failed: specific error", value)
    }

    // endregion
}
