package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface SelectWorldStepFailure : SelectWorldFailure, CreateWorldFailure {
    data class Other(val failure: DatabaseFailure) : SelectWorldFailure
}

data class SelectWorldStep(val userId: Int) : Step<Int, SelectWorldFailure, Int> {
    override suspend fun process(input: Int): Result<SelectWorldFailure, Int> {
        return useConnection({ SelectWorldStepFailure.Other(it) }) {
            prepareStatement("update users set selected_world = ? where id = ?")
                .apply { setInt(1, input); setInt(2, userId) }
                .executeUpdate()
            return@useConnection Result.success(input)
        }
    }
}