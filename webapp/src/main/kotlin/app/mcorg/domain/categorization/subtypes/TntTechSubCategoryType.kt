package app.mcorg.domain.categorization.subtypes

enum class TntTechSubCategoryType(override val displayName: String) : SubCategoryType {
    DUPERS("Dupers"),
    COMPRESSORS("Compressors"),
    TRANSPORT_CANNONS("Transport cannons"),
    DIGGING_CANNONS("Digging cannons"),
    ARROW_CANNONS("Arrow cannons"),
    WEAPONRY("Weaponry")
}