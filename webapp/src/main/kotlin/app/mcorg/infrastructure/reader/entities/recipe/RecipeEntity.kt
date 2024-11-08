package app.mcorg.infrastructure.reader.entities.recipe

import app.mcorg.domain.AnyOf
import app.mcorg.domain.minecraft.model.Item
import app.mcorg.domain.minecraft.model.Recipe

interface RecipeEntity {
    fun toRecipe() = Recipe(getInput(), getOutput())
    fun getInput(): List<Pair<AnyOf<Item>, Int>>
    fun getOutput(): Pair<Item, Int>
}



