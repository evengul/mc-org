package app.mcorg.pipeline.world

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

data class ValidateWorldMemberRole<T>(val user: TokenProfile, val role: Role, val worldId: Int) : Step<T, AppFailure, T> {
    override suspend fun process(input: T): Result<AppFailure, T> {
        val cacheKey = "${user.id}:$worldId:${role.level}"

        // Check cache first
        CacheManager.worldMemberRole.getIfPresent(cacheKey)?.let { hasRole ->
            return if (hasRole) Result.success(input)
            else Result.failure(AppFailure.AuthError.NotAuthorized)
        }

        // Cache miss - query DB
        val result = DatabaseSteps.query<T, Boolean>(
            sql = SafeSQL.select("""
                SELECT EXISTS (
                    SELECT 1
                    FROM world_members
                    WHERE user_id = ? AND world_id = ? AND world_role <= ?
                ) AS has_role
            """.trimIndent()),
            parameterSetter = { statement, _ ->
                statement.setInt(1, user.id)
                statement.setInt(2, worldId)
                statement.setInt(3, role.level)
            },
            resultMapper = {
                if (it.next()) {
                    it.getBoolean("has_role")
                } else {
                    false
                }
            }
        ).process(input)

        // Cache the result
        if (result is Result.Success) {
            CacheManager.worldMemberRole.put(cacheKey, result.value)
        }

        return when (result) {
            is Result.Success -> if (result.value) Result.success(input) else Result.failure(AppFailure.AuthError.NotAuthorized)
            is Result.Failure -> Result.failure(AppFailure.AuthError.NotAuthorized)
        }
    }
}
