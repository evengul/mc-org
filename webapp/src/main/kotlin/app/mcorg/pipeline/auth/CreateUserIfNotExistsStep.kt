package app.mcorg.pipeline.auth

import app.mcorg.domain.model.minecraft.MinecraftProfile
import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.getReturnedId
import app.mcorg.pipeline.useConnection
import kotlinx.coroutines.runBlocking

sealed interface CreateUserIfNotExistsFailure : SignInLocallyFailure, SignInWithMinecraftFailure {
    data class Other(val failure: DatabaseFailure) : CreateUserIfNotExistsFailure
}

data object CreateUserIfNotExistsStep : Step<MinecraftProfile, CreateUserIfNotExistsFailure, User> {
    override suspend fun process(input: MinecraftProfile): Result<CreateUserIfNotExistsFailure, User> {
        return useConnection({ CreateUserIfNotExistsFailure.Other(it) }) {
            prepareStatement("select id, username from users where username = ?")
                .apply { setString(1, input.username) }
                .executeQuery()
                .apply {
                    if (next()) {
                        return@useConnection Result.success(
                            User(
                                id = getInt("id"),
                                username = getString("username"),
                            )
                        )
                    }
                }

            val id = prepareStatement("insert into users(username, email) values (?, ?) returning id")
                .apply { setString(1, input.username); setString(2, input.email) }
                .getReturnedId(CreateUserIfNotExistsFailure.Other(DatabaseFailure.NoIdReturned))

            return@useConnection runBlocking {
                id.map { User(it, input.username) }
            }
        }
    }
}
