package app.mcorg.data.minecraft

import app.mcorg.domain.model.minecraft.MinecraftVersion
import java.nio.file.Path

object ServerPathResolvers {
    fun resolveItemTagsPath(mainDirectory: Path, version: MinecraftVersion.Release): Path {
        return if (version >= MinecraftVersion.Release(1, 21, 0)) {
            mainDirectory.resolve("tags").resolve("item")
        } else mainDirectory.resolve("tags").resolve("items")
    }

    fun resolveBlockTagsPath(mainDirectory: Path, version: MinecraftVersion.Release): Path {
        return if (version >= MinecraftVersion.Release(1, 21, 0)) {
            mainDirectory.resolve("tags").resolve("block")
        } else mainDirectory.resolve("tags").resolve("blocks")
    }

    fun resolveLootTablesPath(mainDirectory: Path, version: MinecraftVersion.Release): Path {
        return if (version >= MinecraftVersion.Release(1, 21, 0)) {
            mainDirectory.resolve("loot_table")
        } else mainDirectory.resolve("loot_tables")
    }

    fun resolveRecipesPath(mainDirectory: Path, version: MinecraftVersion.Release): Path {
        return if (version >= MinecraftVersion.Release(1, 21, 0)) {
            mainDirectory.resolve("recipe")
        } else mainDirectory.resolve("recipes")
    }

    /** Villager trade JSON files were introduced in 26.1 — earlier versions return a
     *  non-existent path and the extractor returns an empty list. */
    fun resolveVillagerTradesPath(mainDirectory: Path): Path {
        return mainDirectory.resolve("villager_trade")
    }
}
