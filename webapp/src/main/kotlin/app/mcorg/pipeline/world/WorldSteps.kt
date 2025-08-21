package app.mcorg.pipeline.world

import app.mcorg.domain.model.world.World
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.failure.HandleGetWorldFailure

val worldQueryStep = DatabaseSteps.query<Int, HandleGetWorldFailure, World>(
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
    resultMapper = { if(it.next()) it.toWorld() else throw IllegalStateException("World should exist at this point") }
)