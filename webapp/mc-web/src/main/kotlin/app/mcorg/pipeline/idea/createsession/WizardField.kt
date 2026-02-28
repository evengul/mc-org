package app.mcorg.pipeline.idea.createsession

import kotlinx.serialization.Serializable

@Serializable
enum class WizardField {
    NAME,
    DESCRIPTION,
    DIFFICULTY,
    AUTHOR,
    VERSION_RANGE,
    LITEMATICA_VALUES,
    ITEM_REQUIREMENTS,
    CATEGORY_DATA,
}