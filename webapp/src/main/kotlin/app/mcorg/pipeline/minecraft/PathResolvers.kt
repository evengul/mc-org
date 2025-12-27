package app.mcorg.pipeline.minecraft

import app.mcorg.domain.model.minecraft.MinecraftVersion
import java.nio.file.Path

object ServerPathResolvers {
    fun resolveItemTagsPath(mainDirectory: Path, version: MinecraftVersion.Release): Path {
        return if (version >= MinecraftVersion.Release(1, 21, 0)) {
            mainDirectory.resolve("data").resolve("tags").resolve("item")
        } else mainDirectory.resolve("data").resolve("tags").resolve("items")
    }

    fun resolveBlockTagsPath(mainDirectory: Path, version: MinecraftVersion.Release): Path {
        return if (version >= MinecraftVersion.Release(1, 21, 0)) {
            mainDirectory.resolve("data").resolve("tags").resolve("block")
        } else mainDirectory.resolve("data").resolve("tags").resolve("blocks")
    }

    fun resolveLootTablesPath(mainDirectory: Path): Path {
        return mainDirectory.resolve("data").resolve("minecraft").resolve("loot_table")
    }
}