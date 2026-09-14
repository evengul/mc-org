package app.mcorg.domain.model.project

/**
 * Lifecycle state of a project — a separate axis from [ProjectStage], which tracks
 * progress within the project. State drives grouping and sorting on the world's
 * project list (Field Log): active work on top, paused parked, terminal states shelved.
 */
enum class ProjectState {
    PENDING,
    ACTIVE,
    PAUSED,
    DONE,
    CANCELLED,
    ARCHIVED,

    /**
     * Built, worked, and no longer supplies anything — the base moved, or a version upgrade
     * broke the farm (MCO-541). Not a failure and not a mistake, which is what separates it
     * from CANCELLED; and unlike ARCHIVED it can only be reached from DONE, so it always means
     * "this was built". Coming back online is a return to DONE, not a re-import.
     */
    DECOMMISSIONED;

    val isTerminal: Boolean
        get() = this == DONE || this == CANCELLED || this == ARCHIVED || this == DECOMMISSIONED

    fun allowedTransitions(): Set<ProjectState> = when (this) {
        PENDING -> setOf(ACTIVE, CANCELLED, ARCHIVED)
        ACTIVE -> setOf(PAUSED, DONE, CANCELLED)
        PAUSED -> setOf(ACTIVE, CANCELLED, ARCHIVED)
        DONE -> setOf(ACTIVE, ARCHIVED, DECOMMISSIONED)
        CANCELLED -> setOf(PENDING, ARCHIVED)
        ARCHIVED -> setOf(PENDING)
        DECOMMISSIONED -> setOf(DONE, ARCHIVED)
    }

    fun canTransitionTo(target: ProjectState): Boolean = target in allowedTransitions()

    companion object {
        fun fromStage(stage: ProjectStage): ProjectState = when (stage) {
            ProjectStage.IDEA, ProjectStage.DESIGN, ProjectStage.PLANNING -> PENDING
            ProjectStage.RESOURCE_GATHERING, ProjectStage.BUILDING, ProjectStage.TESTING -> ACTIVE
            ProjectStage.COMPLETED -> DONE
        }
    }
}
