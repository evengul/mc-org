package app.mcorg.domain.model.idea.schema

import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.idea.commonsteps.ResolveDynamicOptionsStep
import app.mcorg.presentation.templated.common.form.searchableselect.SearchableSelectOption
import kotlinx.coroutines.runBlocking

// TODO: Actual DB caching solution
private val cache: MutableMap<MinecraftVersionRange, List<SearchableSelectOption<String>>> = mutableMapOf()

data class DynamicOptionsConfig(
    val source: DynamicOptionsSource,
    val versionDependent: Boolean = true,
) {
    companion object {
        fun items() = DynamicOptionsConfig(DynamicOptionsSource.ITEMS)
    }

    fun resolve(versionRange: MinecraftVersionRange = MinecraftVersionRange.Unbounded): List<SearchableSelectOption<String>> {
        return runBlocking {
            if (!cache.containsKey(versionRange)) {
                cache[versionRange] = ResolveDynamicOptionsStep(versionRange).process(this@DynamicOptionsConfig).getOrNull() ?: emptyList()
            }
            @Suppress("UNCHECKED_CAST")
            return@runBlocking cache[versionRange] ?: emptyList()
        }
    }
}

enum class DynamicOptionsSource {
    ITEMS,
    MOBS,
    ENCHANTMENTS
}
