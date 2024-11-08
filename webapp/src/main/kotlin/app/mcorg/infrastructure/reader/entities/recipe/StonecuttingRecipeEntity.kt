package app.mcorg.infrastructure.reader.entities.recipe

import app.mcorg.domain.AnyOf
import app.mcorg.domain.minecraft.model.Item
import kotlinx.serialization.Serializable

@Serializable
data class StonecuttingRecipeEntity(
    val type: String,
    val ingredient: String,
    val result: RecipeResultEntity
) : RecipeEntity {
    override fun getInput(): List<Pair<AnyOf<Item>, Int>> {
        return listOf(AnyOf.single(Item(ingredient)) to 1)
    }

    override fun getOutput(): Pair<Item, Int> {
        return Item(result.id) to result.count
    }
}
