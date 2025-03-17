package app.mcorg.presentation.mappers.user

import app.mcorg.presentation.entities.user.AddUserRequest
import app.mcorg.presentation.mappers.InputMappers
import app.mcorg.presentation.mappers.required
import io.ktor.http.*

fun InputMappers.Companion.addUserInputMapper(parameters: Parameters): AddUserRequest {
    val username = parameters.required("newUser")
    return AddUserRequest(username)
}