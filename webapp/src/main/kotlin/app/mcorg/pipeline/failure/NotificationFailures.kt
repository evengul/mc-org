package app.mcorg.pipeline.failure

sealed interface NotificationFailures {
    data class ValidationError(val errors: List<ValidationFailure>) : NotificationFailures
    data object DatabaseError : NotificationFailures
    data object NotificationNotFound : NotificationFailures
    data object InsufficientPermissions : NotificationFailures
}
