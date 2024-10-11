package app.mcorg.domain.categorization.subtypes

enum class OtherSubCategoryType(override val displayName: String) : SubCategoryType {
    INSTANT_WIRES("Instant wires"),
    LOGIC_COMPUTATION("Logic computation")
}