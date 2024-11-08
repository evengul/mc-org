package app.mcorg.infrastructure.reader.entities.recipe

import app.mcorg.domain.AnyOf
import app.mcorg.domain.minecraft.model.Item
import app.mcorg.infrastructure.reader.entities.serializer.ListOrStringSerializer
import kotlinx.serialization.Serializable

@Serializable
data class ShapelessCraftingRecipeEntity(
    val type: String,
    val category: String,
    val group: String? = null,
    @Serializable(with = ListOrStringSerializer::class) val ingredients: Any,
    val result: RecipeResultEntity
) : RecipeEntity {
    override fun getInput(): List<Pair<AnyOf<Item>, Int>> {
        val input = mutableMapOf<List<*>, Int>()

        if (ingredients is List<*>) {
            ingredients.forEach {
                if (it is String) {
                    input.compute(listOf(it)) { _, i -> i?.plus(1) ?: 1}
                } else if (it is List<*>) {
                    input.compute(it) { _, i -> i?.plus(1) ?: 1}
                }
            }
        }

        return input.entries.map { AnyOf(it.key.filterIsInstance<String>().map { itemId -> Item(itemId) }) to it.value }
    }

    override fun getOutput(): Pair<Item, Int> {
        return Item(result.id) to result.count
    }
}
