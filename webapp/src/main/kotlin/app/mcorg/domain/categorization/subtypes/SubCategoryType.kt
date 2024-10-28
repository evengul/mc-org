package app.mcorg.domain.categorization.subtypes

import app.mcorg.domain.categorization.CategoryType

interface SubCategoryType {
    val displayName: String
    val categoryType: CategoryType
}