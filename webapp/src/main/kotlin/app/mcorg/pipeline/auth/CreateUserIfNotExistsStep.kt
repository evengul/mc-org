package app.mcorg.pipeline.auth

import app.mcorg.domain.api.Users
import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step

sealed interface CreateUserIfNotExistsFailure : SignInLocallyFailure

data class CreateUserIfNotExistsInput(val username: String, val email: String)

data class CreateUserIfNotExistsStep(private val users: Users) : Step<CreateUserIfNotExistsInput, CreateUserIfNotExistsFailure, User> {
    override fun process(input: CreateUserIfNotExistsInput): Result<CreateUserIfNotExistsFailure, User> {
        users.getUser(input.username)?.let {
            return Result.success(it)
        }

        val userId = users.createUser(input.username, input.email)
        return Result.success(users.getUser(userId)!!)
    }
}
