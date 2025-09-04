package app.mcorg.pipeline.world.settings

import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.failure.ValidationFailure

sealed interface UpdateWorldVersionFailures {
    data class ValidationError(val errors: List<ValidationFailure>) : UpdateWorldVersionFailures
    data class DatabaseError(val cause: DatabaseFailure) : UpdateWorldVersionFailures
}
