package app.mcorg.domain.minecraft.model

data class Barter(val item: Item, val amount: IntRange)

fun Barter.cost() = Item("Gold Ingot") to 1
