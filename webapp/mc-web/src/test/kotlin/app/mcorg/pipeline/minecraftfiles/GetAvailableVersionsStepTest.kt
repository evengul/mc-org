package app.mcorg.pipeline.minecraftfiles

import app.mcorg.config.MojangLauncherMetaApiConfig
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.GetAvailableVersionsStep
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetAvailableVersionsStepTest {

    private fun loadFixture(path: String): String =
        this::class.java.classLoader.getResource(path)!!.readText()

    @BeforeEach
    fun setup() {
        MojangLauncherMetaApiConfig.resetProvider()
    }

    @AfterEach
    fun tearDown() {
        MojangLauncherMetaApiConfig.resetProvider()
    }

    @Test
    fun `returns sorted releases at or above 1_18_0`() = runBlocking {
        MojangLauncherMetaApiConfig.useFakeProvider { _, _ ->
            Result.success(loadFixture("fixtures/mojang/version_manifest_v2.json"))
        }

        val result = GetAvailableVersionsStep.process(Unit)
        val versions = assertIs<Result.Success<List<MinecraftVersion.Release>>>(result).value

        // Manifest contains: 26.1.1, 1.21.3, 1.20.4, 1.19, 1.19.2, 1.17.1 (release),
        // plus 24w46a, b1.7.3, a1.0.0 (non-release / unparseable).
        // 1.17.1 is below the 1.18.0 floor.
        assertEquals(
            listOf(
                MinecraftVersion.Release(1, 19, 0),
                MinecraftVersion.Release(1, 19, 2),
                MinecraftVersion.Release(1, 20, 4),
                MinecraftVersion.Release(1, 21, 3),
                MinecraftVersion.Release(26, 1, 1)
            ),
            versions
        )
    }

    @Test
    fun `handles both two-part and three-part release ids`() = runBlocking {
        MojangLauncherMetaApiConfig.useFakeProvider { _, _ ->
            Result.success(loadFixture("fixtures/mojang/version_manifest_v2.json"))
        }

        val result = GetAvailableVersionsStep.process(Unit)
        val versions = assertIs<Result.Success<List<MinecraftVersion.Release>>>(result).value

        assertTrue(versions.contains(MinecraftVersion.Release(1, 19, 0)), "Expected 1.19 (two-part) to parse as 1.19.0")
        assertTrue(versions.contains(MinecraftVersion.Release(1, 19, 2)), "Expected 1.19.2 (three-part) to parse directly")
    }

    @Test
    fun `excludes non-release types`() = runBlocking {
        MojangLauncherMetaApiConfig.useFakeProvider { _, _ ->
            Result.success(loadFixture("fixtures/mojang/version_manifest_v2.json"))
        }

        val result = GetAvailableVersionsStep.process(Unit)
        val versions = assertIs<Result.Success<List<MinecraftVersion.Release>>>(result).value

        // 24w46a (snapshot) would parse as a Snapshot, not a Release, so it's excluded by type filter.
        // a1.0.0 (old_alpha) and b1.7.3 (old_beta) are unparseable as Release — type filter excludes them first.
        assertTrue(versions.none { it.toString().contains("24w") })
        assertFalse(versions.contains(MinecraftVersion.Release(1, 17, 1)))
    }

    @Test
    fun `skips unparseable release ids without failing`() = runBlocking {
        val manifest = """
            {
              "latest": { "release": "1.20.4", "snapshot": "24w46a" },
              "versions": [
                {
                  "id": "1.20.4", "type": "release",
                  "url": "https://example.invalid/1.20.4.json",
                  "time": "2023-12-07T12:00:00+00:00",
                  "releaseTime": "2023-12-07T12:00:00+00:00",
                  "sha1": "x", "complianceLevel": 1
                },
                {
                  "id": "garbage-version", "type": "release",
                  "url": "https://example.invalid/garbage.json",
                  "time": "2023-12-07T12:00:00+00:00",
                  "releaseTime": "2023-12-07T12:00:00+00:00",
                  "sha1": "y", "complianceLevel": 1
                }
              ]
            }
        """.trimIndent()

        MojangLauncherMetaApiConfig.useFakeProvider { _, _ -> Result.success(manifest) }

        val result = GetAvailableVersionsStep.process(Unit)
        val versions = assertIs<Result.Success<List<MinecraftVersion.Release>>>(result).value

        assertEquals(listOf(MinecraftVersion.Release(1, 20, 4)), versions)
    }

    @Test
    fun `returns failure when manifest fetch fails`() {
        runBlocking {
            MojangLauncherMetaApiConfig.useFakeProvider { _, _ ->
                Result.failure(AppFailure.ApiError.NetworkError)
            }

            val result = GetAvailableVersionsStep.process(Unit)
            assertIs<Result.Failure<AppFailure.ApiError>>(result)
        }
    }
}
