package app.mcorg.pipeline.world

import app.mcorg.domain.model.world.World
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.failure.HandleGetWorldFailure
import app.mcorg.pipeline.failure.ValidationFailure

val getWorldIdStep = ValidationSteps.requiredInt("worldId") {
    when(it) {
        is ValidationFailure.MissingParameter -> HandleGetWorldFailure.WorldIdRequired
        is ValidationFailure.InvalidFormat,
        is ValidationFailure.InvalidLength,
        is ValidationFailure.InvalidValue,
        is ValidationFailure.OutOfRange,
        is ValidationFailure.CustomValidation -> HandleGetWorldFailure.InvalidWorldId
    }
}

val worldQueryStep = DatabaseSteps.query<Int, HandleGetWorldFailure, World?>(
    getWorldQuery,
    parameterSetter = { statement, input ->
        statement.setInt(1, input)
    },
    errorMapper = {
        when(it) {
            is DatabaseFailure.NotFound -> HandleGetWorldFailure.WorldNotFound
            else -> HandleGetWorldFailure.SystemError("A system error occurred while fetching the world.")
        }
    },
    resultMapper = { if(it.next()) it.toWorld() else null }
)

val validateWorldExistsStep = ValidationSteps.validateNonNull<HandleGetWorldFailure.WorldNotFound, World>({ HandleGetWorldFailure.WorldNotFound })