package app.mcorg.infrastructure.reader.entities.recipe

import app.mcorg.domain.AnyOf
import app.mcorg.domain.minecraft.model.Item
import app.mcorg.infrastructure.reader.serializer.ListOrStringSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CampfireCookingRecipeEntity(
    val type: String,
    val category: String,
    @SerialName("cookingtime")
    val cookingTime: Int,
    val experience: Double,
    @Serializable(with = ListOrStringSerializer::class)
    val ingredient: Any,
    val result: RecipeResultEntity
) : RecipeEntity {
    override fun getInput(): List<Pair<AnyOf<Item>, Int>> {
        if (ingredient is String) {
            return listOf(AnyOf.single(Item(ingredient)) to 1)
        } else if (ingredient is List<*>) {
            return listOf(AnyOf(ingredient.filterIsInstance<String>().map { Item(it) }) to 1)
        }
        throw IllegalStateException("Ingredient should be String or List<String>")
    }

    override fun getOutput(): Pair<Item, Int> {
        return Item(result.id) to 1
    }
}
