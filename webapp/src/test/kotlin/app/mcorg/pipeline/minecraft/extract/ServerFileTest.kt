package app.mcorg.pipeline.minecraft.extract

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
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
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

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
            Pipeline.create<AppFailure, Unit>()
                .pipe(GetAvailableVersionsStep)
                .pipe(GetServerUrlsStep)
                .pipe(object :
                    Step<List<Pair<MinecraftVersion.Release, URI>>, AppFailure, List<Pair<MinecraftVersion.Release, Path>>> {
                    override suspend fun process(input: List<Pair<MinecraftVersion.Release, URI>>): Result<AppFailure, List<Pair<MinecraftVersion.Release, Path>>> {
                        val innerPipeline = Pipeline.create<AppFailure, Pair<MinecraftVersion.Release, URI>>()
                            .pipe(GetServerFileStep)
                            .pipe(ExtractRelevantMinecraftFilesStep {
                                resourcesPath.resolve(it).createDirectories()
                            })

                        if (input.isEmpty()) {
                            println("No server files to process")
                            return Result.success(emptyList())
                        }

                        val result = input.map {
                            val innerResult = innerPipeline.execute(it)
                            try {
                                delay(500)
                            } catch (e: Exception) {
                                println("Error in processing $e")
                            }
                            innerResult
                        }

                        val errors = result.filterIsInstance<Result.Failure<AppFailure>>()
                        if (errors.isNotEmpty()) {
                            return Result.failure(
                                AppFailure.customValidationError(
                                    "files",
                                    "Couldn't process some server files: ${errors.map { it.error }}"
                                )
                            )
                        }

                        val successes =
                            result.filterIsInstance<Result.Success<Pair<MinecraftVersion.Release, Path>>>()
                        return Result.success(successes.map { it.value })
                    }
                })
                .execute(Unit)
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