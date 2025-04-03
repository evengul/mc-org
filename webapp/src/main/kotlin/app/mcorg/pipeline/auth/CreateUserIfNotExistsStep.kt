package app.mcorg.pipeline.auth

import app.mcorg.domain.api.Users
import app.mcorg.domain.model.minecraft.MinecraftProfile
import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step

sealed interface CreateUserIfNotExistsFailure : SignInLocallyFailure, SignInWithMinecraftFailure

data class CreateUserIfNotExistsStep(private val users: Users) : Step<MinecraftProfile, CreateUserIfNotExistsFailure, User> {
    override fun process(input: MinecraftProfile): Result<CreateUserIfNotExistsFailure, User> {
        users.getUser(input.username)?.let {
            return Result.success(it)
        }

        val userId = users.createUser(input.username, input.email)
        return Result.success(users.getUser(userId)!!)
    }
}
