package app.mcorg.domain.categorization.subtypes

enum class FarmSubcategoryType(override val displayName: String) : SubCategoryType {
    TREE_FARM("Tree Farm"),
    SLIME_FARM("Slime farm"),
    IRON_FARM("Iron farm"),
    SHULKER_FARM("Shulker farm"),
    WOOL_FARM("Wool farm"),
    GENERIC_HOSTILE_MOB_FARM("Generic hostile mob farm"),
    CREEPER_FARM("Creeper farm"),
    ENDERMAN_FARM("Enderman farm"),
    WITCH_HUT_FARM("Witch hut farm"),
    GUARDIAN_FARM("Guardian farm"),
    SPAWNER_FARM("Spawner farm"),
}