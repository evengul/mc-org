package app.mcorg.domain.minecraft.model

data class Loot(val source: String, val loot: List<Pair<Item, IntRange>>)
