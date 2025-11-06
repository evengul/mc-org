package app.mcorg.pipeline.auth.commonsteps

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

object UpdateLastSignInStep : Step<String, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: String): Result<AppFailure.DatabaseError, Unit> {
        return DatabaseSteps.update<String>(
            sql = SafeSQL.update("UPDATE minecraft_profiles set last_login = NOW() WHERE username = ?"),
            parameterSetter = { statement, _ -> statement.setString(1, input) }
        ).process(input).map { }
    }
}