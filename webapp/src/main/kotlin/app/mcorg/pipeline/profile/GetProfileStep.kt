package app.mcorg.pipeline.profile

import app.mcorg.domain.model.users.Profile
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.useConnection

object GetProfileStep : Step<Int, DatabaseFailure, Profile> {
    override suspend fun process(input: Int): Result<DatabaseFailure, Profile> {
        return useConnection {
            val resultSet =
                prepareStatement("select id,username,email,profile_photo,selected_world,technical_player from users where id=?")
                    .apply { setInt(1, input) }
                    .executeQuery()
            if (resultSet.next()) {
                return@useConnection Result.success(
                    Profile(
                        resultSet.getInt(1),
                        resultSet.getString(2),
                        resultSet.getString(3),
                        resultSet.getString(4),
                        resultSet.getInt(5).takeIf { it > 0 },
                        resultSet.getBoolean(6)
                    )
                )
            }
            return@useConnection Result.failure(DatabaseFailure.NotFound)
        }
    }
}