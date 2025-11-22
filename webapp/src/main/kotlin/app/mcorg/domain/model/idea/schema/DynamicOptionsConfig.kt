package app.mcorg.domain.model.idea.schema

import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.idea.commonsteps.ResolveDynamicOptionsStep
import app.mcorg.presentation.templated.common.form.searchableselect.SearchableSelectOption
import kotlinx.coroutines.runBlocking

// TODO: Actual DB caching solution
private val cache: MutableMap<MinecraftVersionRange, List<SearchableSelectOption<*>>> = mutableMapOf()

data class DynamicOptionsConfig<T>(
    val source: DynamicOptionsSource,
    val versionDependent: Boolean = true,
    val filter: ((T) -> Boolean)? = null,
) {
    companion object {
        fun items() = DynamicOptionsConfig<String>(DynamicOptionsSource.ITEMS)
    }

    fun resolve(versionRange: MinecraftVersionRange = MinecraftVersionRange.Unbounded): List<SearchableSelectOption<T>> {
        return runBlocking {
            if (!cache.containsKey(versionRange)) {
                cache[versionRange] = ResolveDynamicOptionsStep<T>(versionRange).process(this@DynamicOptionsConfig).getOrNull() ?: emptyList()
            }
            @Suppress("UNCHECKED_CAST")
            return@runBlocking (cache[versionRange] as? List<SearchableSelectOption<T>>) ?: emptyList()
        }
    }
}

enum class DynamicOptionsSource {
    ITEMS,
    MOBS,
    ENCHANTMENTS
}
