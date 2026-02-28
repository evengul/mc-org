package app.mcorg.data.minecraft.extract

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
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

    fun getVersions(): Array<Arguments> {
        val basePath = Paths.get("").toAbsolutePath()
        val resourcesPath = basePath.resolve("src/test/resources/servers/extracted")

        if (!resourcesPath.exists()) {
            return emptyArray()
        }

        val entries = resourcesPath.listDirectoryEntries()
        if (entries.isEmpty()) {
            return emptyArray()
        }

        return entries
            .asSequence()
            .map { dir -> dir.toString().substringAfterLast("/").substringAfterLast("\\") }
            .map { MinecraftVersion.Release.fromString(it) }
            .sortedWith { release, release1 -> release1.compareTo(release) }
            .filter { whitelistedVersions.contains(it) }
            .map { Arguments.of(it) }
            .toList()
            .toTypedArray()
    }
}
