package app.mcorg.domain.categorization.subtypes

import app.mcorg.domain.categorization.CategoryType

enum class SlimestoneSubCategoryType(override val displayName: String) : SubCategoryType {
    ENGINE("Engine"),
    ONE_WAY_EXTENSION("One way extension"),
    TWO_WAY_EXTENSION("Two way extension"),
    RETURN_STATION("Return station"),
    QUARRY("Quarry"),
    TRENCHER("Trencher"),
    TUNNEL_BORE("Tunnel bore"),
    WORLD_EATER("World eater"),
    LIQUID_SWEEPER("Liquid sweeper"),
    BEDROCK_BREAKER("Bedrock breaker"),
    OTHER("Other");

    override val categoryType: CategoryType
        get() = CategoryType.SLIMESTONE
}