package app.mcorg.infrastructure.reader.entities.recipe

import app.mcorg.domain.AnyOf
import app.mcorg.domain.minecraft.model.Item

class EmptyRecipeEntity : RecipeEntity {
    override fun getInput(): List<Pair<AnyOf<Item>, Int>> {
        return emptyList()
    }

    override fun getOutput(): Pair<Item, Int> {
        return Item("mcorg:empty") to 0
    }
}
