package app.mcorg.domain.categorization.value

import app.mcorg.domain.categorization.CategoryMarker
import app.mcorg.domain.categorization.Group

@CategoryMarker
data class ValueGroup(
    override val id: String,
    override val name: String,
    override val canBeFiltered: Boolean = true,
    override var value: Group? = Group()
) : CategoryFilter<Group>