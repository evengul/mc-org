package app.mcorg.domain.categorization.value

import app.mcorg.domain.categorization.CategoryMarker

@CategoryMarker
data class EnumFilter<T : Enum<T>>(
    override val id: String,
    override val name: String,
    val clazz: Class<T>,
    override var value: T? = null
) : CategoryFilter<T>
