package app.mcorg.pipeline.world

import app.mcorg.domain.model.world.World
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.failure.DatabaseFailure

object GetUpdatedWorldStep : Step<Int, UpdateWorldFailures.DatabaseError, World> {
    override suspend fun process(input: Int): Result<UpdateWorldFailures.DatabaseError, World> {
        return DatabaseSteps.query<Int, UpdateWorldFailures.DatabaseError, World?>(
            sql = getWorldQuery,
            parameterSetter = { statement, _ ->
                statement.setInt(1, input)
            },
            errorMapper = { UpdateWorldFailures.DatabaseError(it) },
            resultMapper = { if (it.next()) it.toWorld() else null }
        ).process(input).flatMap { world ->
            if (world != null) {
                Result.success(world)
            } else {
                Result.failure(UpdateWorldFailures.DatabaseError(DatabaseFailure.NotFound))
            }
        }
    }
}
