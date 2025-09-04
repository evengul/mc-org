package app.mcorg.pipeline.world.settings

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import java.sql.Timestamp
import java.time.ZonedDateTime

object UpdateWorldDescriptionStep : Step<Pair<Int, UpdateWorldDescriptionInput>, UpdateWorldDescriptionFailures, Int> {
    override suspend fun process(input: Pair<Int, UpdateWorldDescriptionInput>): Result<UpdateWorldDescriptionFailures, Int> {
        val (worldId, updateInput) = input

        return DatabaseSteps.update<Pair<Int, UpdateWorldDescriptionInput>, UpdateWorldDescriptionFailures>(
            SafeSQL.update("""
                UPDATE world 
                SET description = ?, updated_at = ?
                WHERE id = ?
            """),
            { statement, _ ->
                statement.setString(1, updateInput.description)
                statement.setObject(2, Timestamp(ZonedDateTime.now().toInstant().toEpochMilli()))
                statement.setInt(3, worldId)
            },
            errorMapper = { dbFailure ->
                UpdateWorldDescriptionFailures.DatabaseError(dbFailure)
            }
        ).process(input).map { worldId }
    }
}
