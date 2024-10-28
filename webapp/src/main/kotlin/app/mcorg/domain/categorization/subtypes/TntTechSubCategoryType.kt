package app.mcorg.domain.categorization.subtypes

import app.mcorg.domain.categorization.CategoryType

enum class TntTechSubCategoryType(override val displayName: String) : SubCategoryType {
    DUPERS("Dupers"),
    COMPRESSORS("Compressors"),
    TRANSPORT_CANNONS("Transport cannons"),
    DIGGING_CANNONS("Digging cannons"),
    ARROW_CANNONS("Arrow cannons"),
    WEAPONRY("Weaponry");

    override val categoryType: CategoryType
        get() = CategoryType.TNT_TECH
}