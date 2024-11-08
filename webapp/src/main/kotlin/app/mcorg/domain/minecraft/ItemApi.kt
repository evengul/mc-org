package app.mcorg.domain.minecraft

import app.mcorg.domain.minecraft.model.*

interface ItemApi {
    fun getItems() : List<Item>
    fun getRecipes(): List<Recipe>
    fun getTrades(): List<Trade>
    fun getBarters(): List<Barter>
    fun getLoot(): List<Loot>
}