package app.mcorg.pipeline.world.commonsteps

import app.mcorg.domain.model.user.WorldMember
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.world.extractors.toWorldMember

data class GetWorldMemberStep(val worldId: Int) : Step<Int, AppFailure.DatabaseError, WorldMember> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, WorldMember> {
        return DatabaseSteps.query<Int, WorldMember?>(
            sql = SafeSQL.select("""
                SELECT 
                    user_id,
                    display_name,
                    world_id,
                    world_role,
                    created_at,
                    updated_at
                FROM world_members
                WHERE world_id = ? AND user_id = ?
            """.trimIndent()),
            parameterSetter = { statement, userId ->
                statement.setInt(1, worldId)
                statement.setInt(2, userId)
            },
            resultMapper = {
                if (it.next()) {
                    it.toWorldMember()
                } else null
            }
        ).process(input).flatMap {
            if (it == null) {
                Result.failure(AppFailure.DatabaseError.NotFound)
            } else {
                Result.success(it)
            }
        }
    }
}