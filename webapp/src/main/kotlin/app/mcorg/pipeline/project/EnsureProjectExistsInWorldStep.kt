package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface EnsureProjectExistsInWorldFailure : ProjectParamFailure {
    data object ProjectNotFound : EnsureProjectExistsInWorldFailure
    data class Other(val failure: DatabaseFailure) : EnsureProjectExistsInWorldFailure
}

data class EnsureProjectExistsInWorldStep(val worldId: Int) : Step<Int, EnsureProjectExistsInWorldFailure, Int> {
    override suspend fun process(input: Int): Result<EnsureProjectExistsInWorldFailure, Int> {
        return useConnection({ EnsureProjectExistsInWorldFailure.Other(it) }) {
            prepareStatement("select id from project where id = ? and world_id = ?")
                .apply {
                    setInt(1, input)
                    setInt(2, worldId)
                }
                .executeQuery()
                .use { resultSet ->
                    if (resultSet.next()) {
                        Result.success(resultSet.getInt("id"))
                    } else {
                        Result.failure(EnsureProjectExistsInWorldFailure.ProjectNotFound)
                    }
                }
        }
    }
}