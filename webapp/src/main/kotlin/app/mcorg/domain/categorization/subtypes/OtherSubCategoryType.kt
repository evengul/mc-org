package app.mcorg.domain.categorization.subtypes

import app.mcorg.domain.categorization.CategoryType

enum class OtherSubCategoryType(override val displayName: String) : SubCategoryType {
    INSTANT_WIRES("Instant wires"),
    LOGIC_COMPUTATION("Logic computation");

    override val categoryType: CategoryType
        get() = CategoryType.OTHER
}