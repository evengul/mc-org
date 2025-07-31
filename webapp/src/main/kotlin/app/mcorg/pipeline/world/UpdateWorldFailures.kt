package app.mcorg.pipeline.world

import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.failure.ValidationFailure

sealed interface UpdateWorldFailures {
    data class ValidationError(val errors: List<ValidationFailure>) : UpdateWorldFailures
    data object InsufficientPermissions : UpdateWorldFailures
    data class DatabaseError(val cause: DatabaseFailure) : UpdateWorldFailures
}
