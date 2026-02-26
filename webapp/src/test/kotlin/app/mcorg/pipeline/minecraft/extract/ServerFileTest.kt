package app.mcorg.pipeline.minecraft.extract

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.pipelineResult
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.ExtractRelevantMinecraftFilesStep
import app.mcorg.pipeline.minecraft.GetAvailableVersionsStep
import app.mcorg.pipeline.minecraft.GetServerFileStep
import app.mcorg.pipeline.minecraft.GetServerUrlsStep
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.provider.Arguments
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.time.Duration.Companion.milliseconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class ServerFileTest(
    val whitelistedVersions: MinecraftVersionRange = MinecraftVersionRange.Unbounded
) {
    companion object {
        fun versionPath(version: MinecraftVersion.Release): Path {
            return Path.of("src/test/resources/servers/extracted/${version.toString().replace(".0", "")}")
        }
    }

    @BeforeAll
    fun beforeAll() {
        val basePath = Paths.get("").toAbsolutePath()
        val resourcesPath = basePath.resolve("src/test/resources/servers/extracted")

        if (resourcesPath.exists() && resourcesPath.listDirectoryEntries().isNotEmpty()) {
            return
        }

        runBlocking {
            pipelineResult<AppFailure, Unit> {
                val versions = GetAvailableVersionsStep.run(Unit)
                val urls = GetServerUrlsStep.run(versions)

                if (urls.isEmpty()) {
                    println("No server files to process")
                    return@pipelineResult
                }

                val results = urls.map { pair ->
                    val extractResult = when (val fileResult = GetServerFileStep.process(pair)) {
                        is Result.Success -> ExtractRelevantMinecraftFilesStep {
                            resourcesPath.resolve(it).createDirectories()
                        }.process(fileResult.value)
                        is Result.Failure -> fileResult
                    }
                    try { delay(500.milliseconds) } catch (e: Exception) { println("Error in processing $e") }
                    extractResult
                }

                val errors = results.filterIsInstance<Result.Failure<AppFailure>>()
                if (errors.isNotEmpty()) {
                    Result.failure<AppFailure>(
                        AppFailure.customValidationError(
                            "files",
                            "Couldn't process some server files: ${errors.map { it.error }}"
                        )
                    ).bind()
                }
            }
        }
    }

    fun getVersions(): Array<Arguments> {
        val basePath = Paths.get("").toAbsolutePath()
        val resourcesPath = basePath.resolve("src/test/resources/servers/extracted")
        return resourcesPath.listDirectoryEntries()
            .asSequence()
            .map { dir -> dir.toString().substringAfterLast("/").substringAfterLast("\\") }
            .map { MinecraftVersion.Release.fromString(it) }
            .sortedWith { release, release1 -> release1.compareTo(release) }
            .filter { whitelistedVersions.contains(it) }
            .map { Arguments.of(it) }
            .toList()
            .toTypedArray()
            .takeIf { it.isNotEmpty() } ?: run { fail("Directory $resourcesPath not found. Is the BeforeAll not running?") }
    }
}