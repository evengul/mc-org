package app.mcorg.pipeline.idea.commonsteps

import app.mcorg.domain.model.idea.schema.DynamicOptionsConfig
import app.mcorg.domain.model.idea.schema.DynamicOptionsSource
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.templated.common.form.searchableselect.SearchableSelectOption

@Suppress("UNCHECKED_CAST")
data class ResolveDynamicOptionsStep<T>(val versionRange: MinecraftVersionRange = MinecraftVersionRange.Unbounded) : Step<DynamicOptionsConfig<T>, AppFailure.DatabaseError, List<SearchableSelectOption<T>>> {
    override suspend fun process(input: DynamicOptionsConfig<T>): Result<AppFailure.DatabaseError, List<SearchableSelectOption<T>>> {
        return when (input.source) {
            DynamicOptionsSource.ITEMS -> {
                when (val result = GetItemsInVersionRangeStep.process(versionRange)) {
                    is Result.Success -> {
                        val items = result.value
                        val filteredItems = input.filter?.let { filterFunc ->
                            items.filter { filterFunc(it.id as T) }
                        } ?: items
                        val options = filteredItems.map { item ->
                            SearchableSelectOption(
                                value = item.id as T,
                                label = item.name
                            )
                        }
                        Result.success(options)
                    }
                    is Result.Failure -> Result.failure(result.error)
                }
            }

            DynamicOptionsSource.MOBS -> Result.success(listOf(
                SearchableSelectOption(value = "zombie" as T, label = "Zombie"),
                SearchableSelectOption(value = "skeleton" as T, label = "Skeleton"),
                SearchableSelectOption(value = "creeper" as T, label = "Creeper")
            ))
            else -> Result.failure(AppFailure.DatabaseError.NotFound) // TODO: Implement other sources
        }
    }
}