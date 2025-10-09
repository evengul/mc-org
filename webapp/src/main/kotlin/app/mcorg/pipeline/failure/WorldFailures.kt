package app.mcorg.pipeline.failure

sealed interface CreateWorldFailures {
    data class ValidationError(val errors: List<ValidationFailure>) : CreateWorldFailures
    data object DatabaseError : CreateWorldFailures
}

sealed interface HandleGetWorldFailure {
    object WorldIdRequired : HandleGetWorldFailure
    object InvalidWorldId : HandleGetWorldFailure
    object WorldNotFound : HandleGetWorldFailure
    data class SystemError(val message: String) : HandleGetWorldFailure
}