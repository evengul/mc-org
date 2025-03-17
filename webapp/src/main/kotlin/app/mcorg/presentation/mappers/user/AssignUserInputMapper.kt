package app.mcorg.presentation.mappers.user

import app.mcorg.presentation.entities.user.AssignUserOrDeleteAssignmentRequest
import app.mcorg.presentation.entities.user.AssignUserRequest
import app.mcorg.presentation.entities.user.DeleteAssignmentRequest
import app.mcorg.presentation.mappers.InputMappers
import app.mcorg.presentation.mappers.requiredInt
import io.ktor.http.*

fun InputMappers.Companion.assignUserInputMapper(parameters: Parameters): AssignUserOrDeleteAssignmentRequest {
    return when(val userId = parameters.requiredInt("userId")) {
        -1 -> DeleteAssignmentRequest
        else -> AssignUserRequest(userId)
    }
}