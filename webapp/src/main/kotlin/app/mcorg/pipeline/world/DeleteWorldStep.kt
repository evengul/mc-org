package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.failure.DatabaseFailure

object DeleteWorldStep : Step<Int, DatabaseFailure, Unit> {
    override suspend fun process(input: Int): Result<DatabaseFailure, Unit> {
        return DatabaseSteps.update<Int, DatabaseFailure>(
            sql = deleteWorldQuery,
            parameterSetter = { statement, worldId ->
                statement.setInt(1, worldId)
            },
            errorMapper = { it }
        ).process(input).map { Result.success<DatabaseFailure, Unit>(Unit) }
    }
}