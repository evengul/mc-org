package app.mcorg.pipeline.world.settings

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import java.sql.Timestamp
import java.time.ZonedDateTime

object UpdateWorldNameStep : Step<Pair<Int, UpdateWorldNameInput>, UpdateWorldNameFailures, String> {
    override suspend fun process(input: Pair<Int, UpdateWorldNameInput>): Result<UpdateWorldNameFailures, String> {
        val (worldId, updateInput) = input

        return DatabaseSteps.update<Pair<Int, UpdateWorldNameInput>, UpdateWorldNameFailures>(
            SafeSQL.update("""
                UPDATE world 
                SET name = ?, updated_at = ?
                WHERE id = ?
            """),
            { statement, _ ->
                statement.setString(1, updateInput.name)
                statement.setObject(2, Timestamp(ZonedDateTime.now().toInstant().toEpochMilli()))
                statement.setInt(3, worldId)
            },
            errorMapper = { dbFailure ->
                UpdateWorldNameFailures.DatabaseError(dbFailure)
            }
        ).process(input).map { input.second.name }
    }
}
