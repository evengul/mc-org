package app.mcorg.data.minecraft.extract

import app.mcorg.data.minecraft.failure.ExtractionFailure
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.data.minecraft.extract.loot.ExtractLootTables
import app.mcorg.data.minecraft.extract.recipe.ExtractRecipesStep
import java.nio.file.Path

data object ExtractResourceSources : Step<Pair<MinecraftVersion.Release, Path>, ExtractionFailure, Pair<MinecraftVersion.Release, List<ResourceSource>>> {
    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<ExtractionFailure, Pair<MinecraftVersion.Release, List<ResourceSource>>> {
        val (version, path) = input

        val lootTablesResult = ExtractLootTables.process(input)
        val recipesResult = ExtractRecipesStep.process(input)
        val tradesResult = ExtractTrades.process(path)

        return lootTablesResult.flatMap { lootTables ->
            recipesResult.flatMap { recipes ->
                tradesResult.map { trades ->
                    val allSources: List<ResourceSource> = buildList {
                        addAll(lootTables)
                        addAll(recipes)
                        addAll(trades)
                    }
                    Pair(version, allSources)
                }
            }
        }
    }
}

// TODO: Extract after new version comes out that includes villager trades
private data object ExtractTrades : Step<Path, ExtractionFailure, List<ResourceSource>> {
    override suspend fun process(input: Path): Result<ExtractionFailure, List<ResourceSource>> {
        return Result.Success(emptyList())
    }
}
