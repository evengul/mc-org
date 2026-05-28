package app.mcorg.pipeline.resources

import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import io.ktor.http.Parameters
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateResourceRequiredAmountStepTest {

    @Test
    fun `valid required value passes validation`() = runBlocking {
        val params = Parameters.build { append("required", "5") }
        val result = ValidateRequiredAmountInputStep.process(params)
        assertTrue(result is Result.Success)
        assertEquals(5, (result as Result.Success).value)
    }

    @Test
    fun `required value of 1 passes validation`() = runBlocking {
        val params = Parameters.build { append("required", "1") }
        val result = ValidateRequiredAmountInputStep.process(params)
        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).value)
    }

    @Test
    fun `required value of 0 fails validation`() = runBlocking {
        val params = Parameters.build { append("required", "0") }
        val result = ValidateRequiredAmountInputStep.process(params)
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppFailure.ValidationError)
    }

    @Test
    fun `negative required value fails validation`() = runBlocking {
        val params = Parameters.build { append("required", "-1") }
        val result = ValidateRequiredAmountInputStep.process(params)
        assertTrue(result is Result.Failure)
    }

    @Test
    fun `missing required parameter fails validation`() = runBlocking {
        val params = Parameters.build { }
        val result = ValidateRequiredAmountInputStep.process(params)
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppFailure.ValidationError)
    }

    @Test
    fun `non-numeric required value fails validation`() = runBlocking {
        val params = Parameters.build { append("required", "abc") }
        val result = ValidateRequiredAmountInputStep.process(params)
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppFailure.ValidationError)
    }

    @Test
    fun `large valid required value passes validation`() = runBlocking {
        val params = Parameters.build { append("required", "2000000000") }
        val result = ValidateRequiredAmountInputStep.process(params)
        assertTrue(result is Result.Success)
        assertEquals(2000000000, (result as Result.Success).value)
    }
}
