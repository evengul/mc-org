package app.mcorg.domain.categorization

import app.mcorg.domain.categorization.subtypes.SubCategoryType
import app.mcorg.domain.categorization.value.*

@CategoryMarker
data class SubCategory(
    val type: SubCategoryType
) : FilterContainer()
