package app.mcorg.pipeline.world.settings

import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.failure.ValidationFailure

sealed interface UpdateWorldDescriptionFailures {
    data class ValidationError(val errors: List<ValidationFailure>) : UpdateWorldDescriptionFailures
    data class DatabaseError(val cause: DatabaseFailure) : UpdateWorldDescriptionFailures
}
