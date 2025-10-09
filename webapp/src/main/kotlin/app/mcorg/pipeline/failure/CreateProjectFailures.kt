package app.mcorg.pipeline.failure

sealed interface CreateProjectFailures {
    data class ValidationError(val errors: List<ValidationFailure>) : CreateProjectFailures
    data object DatabaseError : CreateProjectFailures
    data object WorldNotFound : CreateProjectFailures
    data object InsufficientPermissions : CreateProjectFailures
}
