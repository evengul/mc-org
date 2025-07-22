package app.mcorg.pipeline.failure

sealed interface CreateWorldFailures {
    data class ValidationError(val errors: List<ValidationFailure>) : CreateWorldFailures
    data object DatabaseError : CreateWorldFailures
}