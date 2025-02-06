package app.mcorg.infrastructure.reader

import app.mcorg.domain.minecraft.ItemApi
import app.mcorg.domain.minecraft.model.*
import app.mcorg.infrastructure.reader.entities.loot.toLoot

class ItemReader : ItemApi, NameReader("item.minecraft") {

    private val recipeReader = RecipeReader()
    private val lootReader = LootReader()

    override fun getItems(): List<Item> {
        return super.getNames()
            .map { Item(it.second) }
    }

    override fun getRecipes() = recipeReader.getValues().map { it.toRecipe() }

    override fun getLoot(): List<Loot> {
        return lootReader.getValues().map { it.toLoot() }
    }

    override fun getTrades(): List<Trade> {
        TODO("Not yet implemented")
    }

    override fun getBarters(): List<Barter> {
        TODO("Not yet implemented")
    }
}