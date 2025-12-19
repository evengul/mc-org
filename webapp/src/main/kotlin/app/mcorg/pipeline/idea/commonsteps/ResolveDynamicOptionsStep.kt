package app.mcorg.pipeline.idea.commonsteps

import app.mcorg.domain.model.idea.schema.DynamicOptionsConfig
import app.mcorg.domain.model.idea.schema.DynamicOptionsSource
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.templated.common.form.searchableselect.SearchableSelectOption

@Suppress("UNCHECKED_CAST")
data class ResolveDynamicOptionsStep(val versionRange: MinecraftVersionRange = MinecraftVersionRange.Unbounded) : Step<DynamicOptionsConfig, AppFailure.DatabaseError, List<SearchableSelectOption<String>>> {
    override suspend fun process(input: DynamicOptionsConfig): Result<AppFailure.DatabaseError, List<SearchableSelectOption<String>>> {
        return when (input.source) {
            DynamicOptionsSource.ITEMS -> {
                when (val result = GetItemsInVersionRangeStep.process(versionRange)) {
                    is Result.Success -> {
                        val options = result.value.map { item ->
                            SearchableSelectOption(
                                value = item.id,
                                label = item.name
                            )
                        }
                        Result.success(options)
                    }
                    is Result.Failure -> Result.failure(result.error)
                }
            }

            DynamicOptionsSource.MOBS -> Result.success(listOf(
                SearchableSelectOption(value = "zombie", label = "Zombie"),
                SearchableSelectOption(value = "skeleton", label = "Skeleton"),
                SearchableSelectOption(value = "creeper", label = "Creeper")
            ))
            else -> Result.failure(AppFailure.DatabaseError.NotFound) // TODO: Implement other sources
        }
    }
}