package app.mcorg.cli

import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the standalone ingestion entry point's exit-code mapping (MCO-170). The real
 * pipeline (DB + HTTP) is covered elsewhere; here we only assert how outcomes become exit codes and
 * that the pool is always shut down.
 */
class IngestServerFilesTest {

    @Test
    fun `success maps to exit code 0`() {
        var shutdownCalled = false
        val code = runBlocking {
            runIngestion(
                pipeline = { Result.Success(Unit) },
                shutdown = { shutdownCalled = true },
            )
        }
        assertEquals(0, code)
        assertTrue(shutdownCalled, "shutdown must run after a successful pipeline")
    }

    @Test
    fun `pipeline failure maps to exit code 1`() {
        var shutdownCalled = false
        val code = runBlocking {
            runIngestion(
                pipeline = { Result.Failure(AppFailure.DatabaseError.ConnectionError) },
                shutdown = { shutdownCalled = true },
            )
        }
        assertEquals(1, code)
        assertTrue(shutdownCalled, "shutdown must run after a failed pipeline")
    }

    @Test
    fun `unexpected throwable maps to exit code 2`() {
        var shutdownCalled = false
        val code = runBlocking {
            runIngestion(
                pipeline = { throw IllegalStateException("boom") },
                shutdown = { shutdownCalled = true },
            )
        }
        assertEquals(2, code)
        assertTrue(shutdownCalled, "shutdown must run even when the pipeline throws")
    }
}
