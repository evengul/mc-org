package app.mcorg.domain.minecraft.model

import app.mcorg.domain.AnyOf

data class Recipe(
    val input: List<Pair<AnyOf<Item>, Int>>,
    val output: Pair<Item, Int>,
)

