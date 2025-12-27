package app.mcorg.pipeline.minecraft.extract

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.extract.loot.ExtractLootTables
import java.nio.file.Path

data object ExtractResourceSources : Step<Pair<MinecraftVersion.Release, Path>, AppFailure, Pair<MinecraftVersion.Release, List<ResourceSource>>> {
    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<AppFailure, Pair<MinecraftVersion.Release, List<ResourceSource>>> {
        val (version, path) = input

        val lootTablesResult = ExtractLootTables.process(input)
        val recipesResult = ExtractRecipes.process(path)
        val tradesResult = ExtractTrades.process(path)
        val bartersResult = ExtractBarters.process(path)

        return lootTablesResult.flatMap { lootTables ->
            recipesResult.flatMap { recipes ->
                tradesResult.flatMap { trades ->
                    bartersResult.map { barters ->
                        val allSources: List<ResourceSource> = buildList {
                            addAll(lootTables)
                            addAll(recipes)
                            addAll(trades)
                            addAll(barters)
                        }
                        Pair(version, allSources)
                    }
                }
            }
        }
    }
}

private data object ExtractRecipes : Step<Path, AppFailure, List<ResourceSource>> {
    override suspend fun process(input: Path): Result<AppFailure, List<ResourceSource>> {
        TODO("Not yet implemented")
    }
}

// Hardcode < 26.1?
private data object ExtractTrades : Step<Path, AppFailure, List<ResourceSource>> {
    override suspend fun process(input: Path): Result<AppFailure, List<ResourceSource>> {
        TODO("Not yet implemented")
    }
}

// Hardcode? Check loot tables
private data object ExtractBarters : Step<Path, AppFailure, List<ResourceSource>> {
    override suspend fun process(input: Path): Result<AppFailure, List<ResourceSource>> {
        TODO("Not yet implemented")
    }
}