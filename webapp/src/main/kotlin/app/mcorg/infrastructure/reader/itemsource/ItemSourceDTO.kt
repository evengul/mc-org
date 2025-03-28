package app.mcorg.infrastructure.reader.itemsource

data class ItemSourcesDTO(
    val version: String,
    val resourcePackVersion: String,
    val lootTable: List<LootTableDTO>,
    val recipes: List<RecipeDTO>
)

data class LootTableDTO(
    val fromFile: String,
    val type: String,
    val requirements: List<ItemSourceRequirementDTO>,
    val result: List<ItemSourceResultDTO>
)

data class RecipeDTO(
    val fromFile: String,
    val type: String,
    val requirements: List<ItemSourceRequirementDTO>,
    val result: ItemSourceResultDTO
)

data class ItemSourceRequirementDTO(
    val item: String,
    val count: Int
)

data class ItemSourceResultDTO(
    val item: String,
    val count: Int
)