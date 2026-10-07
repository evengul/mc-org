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
 * Only one is [active] at a time, and only its productions supply the world. A project recorded by
 * hand has none: its productions carry no mode and always supply.
 */
data class ProjectProductionMode(
    val id: Int,
    val projectId: Int,
    val name: String,
    val position: Int,
    val active: Boolean,
)
