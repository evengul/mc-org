package app.mcorg.data.minecraft.extract

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.provider.Arguments
import java.nio.file.Path
import java.nio.file.Paths
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
            ServerFileDownloader.downloadAndExtract(resourcesPath)
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
            .takeIf { it.isNotEmpty() } ?: fail("No extracted server data found at $resourcesPath. Is the BeforeAll not running?")
    }
}
