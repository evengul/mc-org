package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.UpdateLastSignInFailure
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

object UpdateLastSignInStep : Step<String, UpdateLastSignInFailure, Unit> {
    override suspend fun process(input: String): Result<UpdateLastSignInFailure, Unit> {
        return DatabaseSteps.update<String, UpdateLastSignInFailure>(
            SafeSQL.update("UPDATE minecraft_profiles set last_login = NOW() WHERE username = ?"),
            { statement, _ -> statement.setString(1, input) },
            { UpdateLastSignInFailure.Other(it) }).process(input).map { }
    }
}