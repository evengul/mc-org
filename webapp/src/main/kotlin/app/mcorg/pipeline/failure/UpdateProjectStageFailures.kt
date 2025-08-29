package app.mcorg.pipeline.failure

sealed interface UpdateProjectStageFailures {
    data class ValidationError(val errors: List<ValidationFailure>) : UpdateProjectStageFailures
    data object DatabaseError : UpdateProjectStageFailures
    data object ProjectNotFound : UpdateProjectStageFailures
    data object InsufficientPermissions : UpdateProjectStageFailures
    data object InvalidStageTransition : UpdateProjectStageFailures
}
