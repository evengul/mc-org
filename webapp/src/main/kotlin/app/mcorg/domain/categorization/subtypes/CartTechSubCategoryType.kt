package app.mcorg.domain.categorization.subtypes

enum class CartTechSubCategoryType(override val displayName: String) : SubCategoryType {
    STORAGE("Storage"),
    TRANSPORT("Transport"),
    COMPUTATIONAL("Computational"),
    FARM_COLLECTION("Farm collection"),
    FURNACE_ARRAYS("Furnace arrays"),
    ITEM_WHITELISTERS("Item whitelisters"),
    LOADING_UNLOADING("Loading/Unloading"),
    STACKERS_UNSTACKERS("Stackers/Unstackers"),
    STATIONARY("Stationary"),
    VILLAGERS_ETC("Villagers etc"),
    ENTITY_TRANSPORT("Entity transport"),
    OTHER("Other")
}