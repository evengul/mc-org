package app.mcorg.pipeline.world.settings

import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.failure.ValidationFailure

sealed interface UpdateWorldNameFailures {
    data class ValidationError(val errors: List<ValidationFailure>) : UpdateWorldNameFailures
    data object NameAlreadyExists : UpdateWorldNameFailures
    data class DatabaseError(val cause: DatabaseFailure) : UpdateWorldNameFailures
}
