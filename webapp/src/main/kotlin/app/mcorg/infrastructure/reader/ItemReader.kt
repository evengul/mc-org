package app.mcorg.infrastructure.reader

import app.mcorg.domain.minecraft.ItemApi
import app.mcorg.domain.minecraft.model.*

class ItemReader : ItemApi, NameReader("item.minecraft") {

    private val recipeReader = RecipeReader()

    override fun getItems(): List<Item> {
        return super.getNames()
            .map { Item(it.second) }
    }

    override fun getRecipes() = recipeReader.getValues().map { it.toRecipe() }

    override fun getTrades(): List<Trade> {
        TODO("Not yet implemented")
    }

    override fun getBarters(): List<Barter> {
        TODO("Not yet implemented")
    }

    override fun getLoot(): List<Loot> {
        TODO("Not yet implemented")
    }
}