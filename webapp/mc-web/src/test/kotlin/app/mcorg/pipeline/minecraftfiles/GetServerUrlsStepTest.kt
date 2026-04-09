package app.mcorg.pipeline.minecraftfiles

import app.mcorg.config.MojangLauncherMetaApiConfig
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GetServerUrlsStepTest {

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

    /**
     * Set up the fake provider to route requests based on URL:
     * - Manifest URL returns the top-level manifest fixture.
     * - Per-version URLs return the matching per-version fixture.
     * - Any URL not mapped returns a 404 failure.
     */
    private fun routeFixtures(metaOverrides: Map<String, String?> = emptyMap()) {
        val defaults = mapOf(
            MojangLauncherMetaApiConfig.getVersionManifestUrl() to "fixtures/mojang/version_manifest_v2.json",
            "https://piston-meta.mojang.com/v1/packages/bbbbbbbb/1.21.3.json" to "fixtures/mojang/version_1_21_3.json",
            "https://piston-meta.mojang.com/v1/packages/cccccccc/1.20.4.json" to "fixtures/mojang/version_1_20_4.json",
            "https://piston-meta.mojang.com/v1/packages/aaaaaaaa/26.1.1.json" to "fixtures/mojang/version_26_1_1.json",
            "https://piston-meta.mojang.com/v1/packages/dddddddd/1.19.json" to "fixtures/mojang/version_1_19.json",
            "https://piston-meta.mojang.com/v1/packages/eeeeeeee/1.19.2.json" to "fixtures/mojang/version_1_19_2.json",
        )
        val merged = defaults + metaOverrides
        MojangLauncherMetaApiConfig.useFakeProvider { _, url ->
            val fixturePath = merged[url]
            if (fixturePath == null) {
                Result.failure(AppFailure.ApiError.HttpError(404, "Not found: $url"))
            } else {
                Result.success(loadFixture(fixturePath))
            }
        }
    }

    @Test
    fun `happy path resolves server URLs for requested versions`() = runBlocking {
        routeFixtures()

        val input = listOf(
            MinecraftVersion.Release(1, 21, 3),
            MinecraftVersion.Release(1, 20, 4)
        )

        val result = GetServerUrlsStep.process(input)
        val pairs = assertIs<Result.Success<List<Pair<MinecraftVersion.Release, URI>>>>(result).value

        assertEquals(
            listOf(
                MinecraftVersion.Release(1, 21, 3) to URI.create("https://piston-data.mojang.com/v1/objects/bbbbbbbb/server.jar"),
                MinecraftVersion.Release(1, 20, 4) to URI.create("https://piston-data.mojang.com/v1/objects/cccccccc/server.jar")
            ),
            pairs
        )
    }

    @Test
    fun `resolves year-based release version`() = runBlocking {
        routeFixtures()

        val input = listOf(MinecraftVersion.Release(26, 1, 1))

        val result = GetServerUrlsStep.process(input)
        val pairs = assertIs<Result.Success<List<Pair<MinecraftVersion.Release, URI>>>>(result).value

        assertEquals(1, pairs.size)
        assertEquals(MinecraftVersion.Release(26, 1, 1), pairs[0].first)
        assertEquals(
            URI.create("https://piston-data.mojang.com/v1/objects/aaaaaaaa/server.jar"),
            pairs[0].second
        )
    }

    @Test
    fun `skips versions not present in the manifest`() = runBlocking {
        routeFixtures()

        val input = listOf(
            MinecraftVersion.Release(1, 21, 3),
            MinecraftVersion.Release(99, 99, 99)
        )

        val result = GetServerUrlsStep.process(input)
        val pairs = assertIs<Result.Success<List<Pair<MinecraftVersion.Release, URI>>>>(result).value

        assertEquals(1, pairs.size)
        assertEquals(MinecraftVersion.Release(1, 21, 3), pairs[0].first)
    }

    @Test
    fun `drops versions whose metadata lacks a server download`() = runBlocking {
        routeFixtures(
            metaOverrides = mapOf(
                "https://piston-meta.mojang.com/v1/packages/cccccccc/1.20.4.json" to "fixtures/mojang/version_no_server.json"
            )
        )

        val input = listOf(
            MinecraftVersion.Release(1, 21, 3),
            MinecraftVersion.Release(1, 20, 4)
        )

        val result = GetServerUrlsStep.process(input)
        val pairs = assertIs<Result.Success<List<Pair<MinecraftVersion.Release, URI>>>>(result).value

        assertEquals(1, pairs.size)
        assertEquals(MinecraftVersion.Release(1, 21, 3), pairs[0].first)
        assertNull(pairs.firstOrNull { it.first == MinecraftVersion.Release(1, 20, 4) })
    }

    @Test
    fun `returns failure when manifest fetch fails`() {
        runBlocking {
            MojangLauncherMetaApiConfig.useFakeProvider { _, _ ->
                Result.failure(AppFailure.ApiError.NetworkError)
            }

            val input = listOf(MinecraftVersion.Release(1, 21, 3))
            val result = GetServerUrlsStep.process(input)
            assertIs<Result.Failure<AppFailure>>(result)
        }
    }
}
