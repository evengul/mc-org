package app.mcorg.pipeline

import app.mcorg.domain.pipeline.Step
import io.ktor.http.Parameters
import io.ktor.http.parametersOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.fail
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Utility functions for testing pipeline patterns
 *
 * These helpers provide common testing patterns for pipeline execution,
 * and Result<E,S> pattern validation.
 */
object TestUtils {

    /**
     * Execute a pipeline step and assert it succeeds, returning the result
     */
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

    /**
     * Execute a pipeline step and assert it fails with expected error type
     */
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

    /**
     * Assert that a Result is successful and return its value
     */
    fun <E, S> assertResultSuccess(result: Result<E, S>): S {
        return when (result) {
            is Result.Success -> result.value
            is Result.Failure -> {
                fail("Expected result to be successful but got failure: ${result.error}")
            }
        }
    }

    /**
     * Assert that a Result is a failure of the expected type
     */
    fun <E, S> assertResultFailure(result: Result<E, S>, expectedErrorClass: Class<out E>): E {
        return when (result) {
            is Result.Success -> {
                fail("Expected result to be failure but got success: ${result.value}")
            }
            is Result.Failure -> {
                val error = result.error
                assertTrue(expectedErrorClass.isInstance(error),
                    "Expected error of type ${expectedErrorClass.simpleName} but got ${error!!::class.simpleName}")
                error
            }
        }
    }

    /**
     * Create test parameters map for form data simulation
     */
    fun createTestParameters(vararg pairs: Pair<String, String>): Parameters {
        return parametersOf(*pairs.map { (key, value) -> key to listOf(value) }.toTypedArray())
    }

    /**
     * Common assertion helpers for validation errors
     */
    fun assertValidationError(
        result: Result<*, *>,
        expectedField: String? = null,
        expectedMessage: String? = null
    ) {
        when (result) {
            is Result.Success -> {
                fail("Expected validation error but got success")
            }
            is Result.Failure -> {
                val errorString = result.error.toString()

                if (expectedField != null) {
                    assertTrue(errorString.contains(expectedField),
                        "Expected validation error to mention field '$expectedField' but got: $errorString")
                }

                if (expectedMessage != null) {
                    assertTrue(errorString.contains(expectedMessage),
                        "Expected validation error to contain message '$expectedMessage' but got: $errorString")
                }
            }
        }
    }
}
