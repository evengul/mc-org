package app.mcorg.infrastructure.reader.entities.recipe

import app.mcorg.domain.AnyOf
import app.mcorg.domain.minecraft.model.Item
import app.mcorg.infrastructure.reader.entities.serializer.KeySerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShapedRecipeEntity(
    val type: String,
    val category: String,
    val group: String? = null,
    @Serializable(with = KeySerializer::class)
    val key: Any,
    val pattern: List<String>,
    val result: RecipeResultEntity,
    @SerialName("show_notification")
    val showNotification: Boolean = true
) : RecipeEntity {
    override fun getInput(): List<Pair<AnyOf<Item>, Int>> {
        val keys = pattern.flatMap { it.toCharArray().toList() }.filter { it != ' ' }
        val count = mutableMapOf<Char, Int>()
        keys.forEach { count[it] = (count[it] ?: 0) + 1 }

        if (key is Map<*, *>) {
            return keys.distinct().map {
                val itemIds = key[it]!!
                if (itemIds is String) {
                    return@map AnyOf.single(Item(itemIds)) to (count[it] ?: 0)
                } else if(itemIds is List<*>) {
                    return@map AnyOf(itemIds.filterIsInstance<String>().map { itemId -> Item(itemId) }) to (count[it] ?: 0)
                }
                throw IllegalStateException("Key must contain either String or List<String>")
            }
        }

        throw IllegalStateException("Key must be a map")
    }

    override fun getOutput(): Pair<Item, Int> {
        return Item(result.id) to result.count
    }
}
