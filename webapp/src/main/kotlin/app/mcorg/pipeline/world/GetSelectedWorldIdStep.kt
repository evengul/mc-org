package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.GetSelectedWorldIdFailure
import app.mcorg.pipeline.useConnection

object GetSelectedWorldIdStep : Step<Int, GetSelectedWorldIdFailure, Int> {
    override suspend fun process(input: Int): Result<GetSelectedWorldIdFailure, Int> {
        return useConnection({ GetSelectedWorldIdFailure.Other(it) }) {
            val statement = prepareStatement("select selected_world from users where id = ? limit 1")
            statement.setInt(1, input)
            val resultSet = statement.executeQuery()
            return@useConnection if (resultSet.next()) {
                val selectedWorld = resultSet.getInt("selected_world")
                if (resultSet.wasNull()) {
                    Result.failure(GetSelectedWorldIdFailure.NoWorldSelected)
                } else {
                    Result.success(selectedWorld)
                }
            } else {
                Result.failure(GetSelectedWorldIdFailure.NoWorldSelected)
            }
        }
    }
}