package app.mcorg.domain.model.project

data class ProjectProduction(
    val id: Int,
    val projectId: Int,
    val itemId: String,
    val name: String,
    val ratePerHour: Int,
    /** The runtime mode this rate belongs to, or null on a project without modes (MCO-413). */
    val modeId: Int? = null,
)

/**
 * One way a built farm can be run — copied from the idea's runtime modes at import (MCO-413).
 *
 * Every mode's productions supply the world once the farm is Done (MCO-588): a runtime mode is a
 * lever the player pulls in game, which Seam cannot see, so it does not track which one is pulled.
 * A project recorded by hand has no modes, only its one list.
 */
data class ProjectProductionMode(
    val id: Int,
    val projectId: Int,
    val name: String,
    val position: Int,
)
