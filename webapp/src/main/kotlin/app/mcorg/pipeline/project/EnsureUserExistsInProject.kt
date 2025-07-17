package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.EnsureUserExistsInProjectFailure
import app.mcorg.pipeline.useConnection

data class EnsureUserExistsInProject(val worldId: Int) : Step<Int, EnsureUserExistsInProjectFailure, Int> {
    override suspend fun process(input: Int): Result<EnsureUserExistsInProjectFailure, Int> {
        return useConnection({ EnsureUserExistsInProjectFailure.Other(it) }) {
            prepareStatement("select 1 from permission where world_id = ? and user_id = ?")
                .apply { setInt(1, worldId); setInt(2, input) }
                .executeQuery()
                .use { resultSet ->
                    when (resultSet.next()) {
                        true -> Result.success(input)
                        false -> Result.failure(EnsureUserExistsInProjectFailure.UserNotFound)
                    }
                }
        }
    }
}