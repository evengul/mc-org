package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import java.sql.Timestamp
import java.time.ZonedDateTime

object UpdateWorldStep : Step<Pair<Int, UpdateWorldInput>, UpdateWorldFailures.DatabaseError, Int> {
    override suspend fun process(input: Pair<Int, UpdateWorldInput>): Result<UpdateWorldFailures.DatabaseError, Int> {
        return DatabaseSteps.update<Pair<Int, UpdateWorldInput>, UpdateWorldFailures.DatabaseError>(
            SafeSQL.update("""
                UPDATE world 
                SET name = ?, description = ?, version = ?, updated_at = ? 
                WHERE id = ?
            """),
            parameterSetter = { statement, (first, second) ->
                statement.setString(1, second.name)
                statement.setString(2, second.description)
                statement.setString(3, second.version.toString())
                statement.setObject(4, Timestamp.valueOf(ZonedDateTime.now().toLocalDateTime()))
                statement.setInt(5, first)
            },
            errorMapper = { UpdateWorldFailures.DatabaseError(it) }
        ).process(input).map { input.first }
    }
}
