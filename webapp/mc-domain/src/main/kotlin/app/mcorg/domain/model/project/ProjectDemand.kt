package app.mcorg.domain.model.project

/**
 * One item a project's derived plan actually needs, and how much of it (MCO-316).
 *
 * This is the *derived* demand, not the declared requirement. Importing a schematic declares
 * finished placed blocks — 5,630 hoppers — while the thing a farm can supply is what those
 * decompose into: 32,947 iron ingots, 74,564 cobblestone. Matching farms against declared rows
 * therefore matched almost nothing, and what it did match was a coincidence of decoration.
 *
 * [group] and [status] are the engine's own classifications, carried so consumers can filter
 * without re-deriving: farm supply wants anything a project produces, while a farm-scale
 * marker (MCO-401) wants raw gathering only.
 */
data class ProjectDemand(
    val projectId: Int,
    val itemId: String,
    val itemName: String,
    val quantity: Long,
    val group: String,
    val status: String,
)
