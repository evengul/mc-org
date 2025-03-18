package app.mcorg.presentation.entities.user

sealed interface AssignUserOrDeleteAssignmentRequest
data class AssignUserRequest(val userId: Int) : AssignUserOrDeleteAssignmentRequest
data object DeleteAssignmentRequest : AssignUserOrDeleteAssignmentRequest