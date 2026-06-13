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
    ARCHIVED;

    val isTerminal: Boolean
        get() = this == DONE || this == CANCELLED || this == ARCHIVED

    fun allowedTransitions(): Set<ProjectState> = when (this) {
        PENDING -> setOf(ACTIVE, CANCELLED, ARCHIVED)
        ACTIVE -> setOf(PAUSED, DONE, CANCELLED)
        PAUSED -> setOf(ACTIVE, CANCELLED, ARCHIVED)
        DONE -> setOf(ACTIVE, ARCHIVED)
        CANCELLED -> setOf(PENDING, ARCHIVED)
        ARCHIVED -> setOf(PENDING)
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
