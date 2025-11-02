package app.mcorg.pipeline.auth.commonsteps

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure

object UpdateLastSignInStep : Step<String, DatabaseFailure, Unit> {
    override suspend fun process(input: String): Result<DatabaseFailure, Unit> {
        return DatabaseSteps.update<String, DatabaseFailure>(
            SafeSQL.update("UPDATE minecraft_profiles set last_login = NOW() WHERE username = ?"),
            { statement, _ -> statement.setString(1, input) },
            { it }).process(input).map { }
    }
}