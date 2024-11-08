package app.mcorg.infrastructure.reader.entities.recipe

import app.mcorg.domain.AnyOf
import app.mcorg.domain.minecraft.model.Item
import kotlinx.serialization.Serializable

@Serializable
data class TransmutationRecipeEntity(
    val type: String,
    val category: String,
    val group: String,
    val input: String,
    val material: String,
    val result: String
) : RecipeEntity {
    override fun getInput(): List<Pair<AnyOf<Item>, Int>> {
        return listOf(
            AnyOf.single(Item(input)) to 1, // TODO: ItemGroup or something to represent all variants
            AnyOf.single(Item(material)) to 1
        )
    }

    override fun getOutput(): Pair<Item, Int> {
        return Item(result) to 1
    }
}
