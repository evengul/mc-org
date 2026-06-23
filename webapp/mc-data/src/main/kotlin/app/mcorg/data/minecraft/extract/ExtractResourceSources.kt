package app.mcorg.data.minecraft.extract

import app.mcorg.data.minecraft.failure.ExtractionFailure
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.data.minecraft.extract.loot.ExtractLootTables
import app.mcorg.data.minecraft.extract.recipe.ExtractRecipesStep

data object ExtractResourceSources : Step<ExtractionContext, ExtractionFailure, List<ResourceSource>> {
    override suspend fun process(input: ExtractionContext): Result<ExtractionFailure, List<ResourceSource>> {
        val lootTablesResult = ExtractLootTables.process(input)
        val recipesResult = ExtractRecipesStep.process(input)
        val tradesResult = ExtractVillagerTradesStep.process(input)

        return lootTablesResult.flatMap { lootTables ->
            recipesResult.flatMap { recipes ->
                tradesResult.map { trades ->
                    lootTables + recipes + trades + SyntheticSources.all()
                }
            }
        }
    }
}
