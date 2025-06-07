package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface UpdateLastSignInFailure : SignInLocallyFailure, SignInWithMinecraftFailure {
    data class Other(val failure: DatabaseFailure) : UpdateLastSignInFailure
}

object UpdateLastSignInStep : Step<String, UpdateLastSignInFailure, Unit> {
    override suspend fun process(input: String): Result<UpdateLastSignInFailure, Unit> {
        return useConnection({ UpdateLastSignInFailure.Other(it) }) {
            prepareStatement("update users set last_login = now() where username = ?")
                .apply { setString(1, input) }
                .executeUpdate()
            return@useConnection Result.success()
        }
    }
}