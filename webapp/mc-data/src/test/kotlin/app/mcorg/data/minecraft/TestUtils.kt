package app.mcorg.data.minecraft

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.Result
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.fail
import kotlin.test.assertIs

object TestUtils {

    fun <I, E, S> executeAndAssertSuccess(
        step: Step<I, E, S>,
        input: I
    ): S = runBlocking {
        when (val result = step.process(input)) {
            is Result.Success -> result.value
            is Result.Failure -> {
                fail("Expected step to succeed but it failed with: ${result.error}")
            }
        }
    }

    inline fun <I, reified E, S> executeAndAssertFailure(
        step: Step<I, E, S>,
        input: I
    ): E = runBlocking {
        when (val result = step.process(input)) {
            is Result.Success -> {
                fail("Expected step to fail but it succeeded with: ${result.value}")
            }
            is Result.Failure -> {
                assertIs<E>(result.error)
                result.error
            }
        }
    }

    fun <E, S> assertResultSuccess(result: Result<E, S>): S {
        return when (result) {
            is Result.Success -> result.value
            is Result.Failure -> {
                fail("Expected result to be successful but got failure: ${result.error}")
            }
        }
    }

    fun <E, S> assertResultFailure(result: Result<E, S>): E {
        return when (result) {
            is Result.Success -> {
                fail("Expected result to be failure but got success: ${result.value}")
            }
            is Result.Failure -> result.error
        }
    }
}
