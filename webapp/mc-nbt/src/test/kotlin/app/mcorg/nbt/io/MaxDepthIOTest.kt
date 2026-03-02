package app.mcorg.nbt.io

import app.mcorg.pipeline.Result
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaxDepthIOTest {
    private class TestMaxDepthIO : MaxDepthIO

    private val io = TestMaxDepthIO()

    @Test
    fun `decrement from 512 gives 511`() {
        val result = io.decrementMaxDepth(512)
        assertTrue(result is Result.Success)
        assertEquals(511, result.getOrThrow())
    }

    @Test
    fun `decrement from 1 gives 0`() {
        val result = io.decrementMaxDepth(1)
        assertTrue(result is Result.Success)
        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun `decrement from 0 fails with MaxDepthReached`() {
        val result = io.decrementMaxDepth(0)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is MaxDepthFailure.MaxDepthReached)
    }

    @Test
    fun `decrement from negative fails with NegativeDepth`() {
        val result = io.decrementMaxDepth(-1)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is MaxDepthFailure.NegativeDepth)
    }
}
