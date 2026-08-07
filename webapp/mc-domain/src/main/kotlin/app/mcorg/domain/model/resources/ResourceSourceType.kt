package app.mcorg.domain.model.resources

/**
 * A resource row's explicitly chosen acquisition source (`resource_gathering.source_type`).
 *
 * Null on `ResourceGatheringItem.sourceType` means "no explicit choice" — the planner is
 * free to pick. An explicit choice encodes user intent and beats ambient world supply
 * (MCO-296): [MANUAL] opts the item out of farm supply entirely; [PROJECT] pins it to a
 * linked project (`solved_by_project_id`).
 *
 * [value] is both the stored DB value and the `type` parameter in the source-picker
 * requests — the two contracts are the same strings by design.
 */
enum class ResourceSourceType(val value: String) {
    /** User chose "Manual gather" in the source picker. */
    MANUAL("manual"),

    /** User linked the row to another project. */
    PROJECT("project");

    companion object {
        /** Parse a stored or submitted value. Null or unrecognised reads as null (no explicit choice). */
        fun fromValue(value: String?): ResourceSourceType? =
            entries.firstOrNull { it.value == value }
    }
}
