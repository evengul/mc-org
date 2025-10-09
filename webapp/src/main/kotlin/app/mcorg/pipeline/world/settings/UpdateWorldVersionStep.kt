package app.mcorg.pipeline.world.settings

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import java.sql.Timestamp
import java.time.ZonedDateTime

object UpdateWorldVersionStep : Step<Pair<Int, UpdateWorldVersionInput>, UpdateWorldVersionFailures, Int> {
    override suspend fun process(input: Pair<Int, UpdateWorldVersionInput>): Result<UpdateWorldVersionFailures, Int> {
        val (worldId, updateInput) = input

        return DatabaseSteps.update<Pair<Int, UpdateWorldVersionInput>, UpdateWorldVersionFailures>(
            SafeSQL.update("""
                UPDATE world 
                SET version = ?, updated_at = ?
                WHERE id = ?
            """),
            { statement, _ ->
                statement.setString(1, updateInput.version.toString())
                statement.setObject(2, Timestamp(ZonedDateTime.now().toInstant().toEpochMilli()))
                statement.setInt(3, worldId)
            },
            errorMapper = { dbFailure ->
                UpdateWorldVersionFailures.DatabaseError(dbFailure)
            }
        ).process(input).map { worldId }
    }
}
