package app.mcorg.pipeline.world

import app.mcorg.domain.model.permissions.Authority
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.CreateWorldFailure
import app.mcorg.pipeline.failure.CreateWorldPermissionFailure
import app.mcorg.pipeline.useConnection

data class CreateWorldPermissionStep(val userId: Int, val authority: Authority) : Step<Int, CreateWorldFailure, Int> {
    override suspend fun process(input: Int): Result<CreateWorldFailure, Int> {
        return useConnection({ CreateWorldPermissionFailure.Other(it) }) {
            prepareStatement("insert into permission (world_id, authority, user_id) values (?, ?, ?)")
                .apply {
                    setInt(1, input)
                    setInt(2, authority.level)
                    setInt(3, userId)
                }
                .executeUpdate()
            return@useConnection Result.success(input)
        }
    }
}