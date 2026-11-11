package app.mcorg.pipeline.minecraftfiles

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectIngestVersionsStepTest {

    private val v1_20_1 = MinecraftVersion.Release(1, 20, 1)
    private val v1_21_0 = MinecraftVersion.Release(1, 21, 0)
    private val v1_21_8 = MinecraftVersion.Release(1, 21, 8)
    private val v26_2_0 = MinecraftVersion.Release(26, 2, 0)

    // Deliberately unsorted: `latest` must not rely on the manifest's order.
    private val available = listOf(v1_21_8, v1_20_1, v26_2_0, v1_21_0)

    private fun select(raw: String?) = SelectIngestVersionsStep.select(available, raw)

    @Test
    fun `unset, blank and all keep every available version`() {
        listOf(null, "", "  ", "all", "ALL").forEach { raw ->
            assertEquals(Result.success(available), select(raw), "raw=$raw")
        }
    }

    @Test
    fun `latest keeps only the newest release`() {
        assertEquals(Result.success(listOf(v26_2_0)), select(" Latest "))
    }

    @Test
    fun `latest on an empty manifest selects nothing`() {
        assertEquals(Result.success(emptyList()), SelectIngestVersionsStep.select(emptyList(), "latest"))
    }

    @Test
    fun `a version list keeps exactly those, accepting the short form of a dot-zero release`() {
        assertEquals(Result.success(listOf(v1_21_8, v1_21_0)), select(" 1.21.8 , 1.21 ,"))
    }

    @Test
    fun `a version that is not available fails the run instead of ingesting less`() {
        val result = select("1.21.8,1.16.5")
        assertTrue(result is Result.Failure, "got $result")
        val error = result.error
        assertTrue(error is AppFailure.IllegalConfigurationError && "1.16.5" in error.reason, "got $error")
    }

    @Test
    fun `an entry that does not parse fails the run`() {
        val result = select("1.21.8,latest-ish")
        assertTrue(result is Result.Failure, "got $result")
        val error = result.error
        assertTrue(error is AppFailure.IllegalConfigurationError && "latest-ish" in error.reason, "got $error")
    }
}
