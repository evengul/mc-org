package app.mcorg.pipeline.failure

import app.mcorg.pipeline.DatabaseFailure

sealed interface AddWorldParticipantFailure
sealed interface RemoveWorldParticipantFailure
sealed interface GetWorldParticipantsFailure

sealed interface AddWorldParticipantStepFailure : AddWorldParticipantFailure {
    data class Other(val failure: DatabaseFailure) : AddWorldParticipantStepFailure
}

sealed interface GetNewParticipantStepFailure : AddWorldParticipantFailure {
    data object NotFound : GetNewParticipantStepFailure
    data class Other(val failure: DatabaseFailure) : GetNewParticipantStepFailure
}

sealed interface GetOtherUsersStepFailure : GetWorldParticipantsFailure {
    data class Other(val failure: DatabaseFailure) : GetOtherUsersStepFailure
}

sealed interface GetUserIdInputFailure : RemoveWorldParticipantFailure {
    data object NotPresent : GetUserIdInputFailure
}

sealed interface GetUsernameInputFailure : AddWorldParticipantFailure {
    data object NotPresent : GetUsernameInputFailure
}

sealed interface RemoveUserAssignmentsInWorldFailure : RemoveWorldParticipantFailure {
    data class Other(val failure: DatabaseFailure) : RemoveUserAssignmentsInWorldFailure
}

sealed interface RemoveUserFromWorldFailure : RemoveWorldParticipantFailure {
    data class Other(val failure: DatabaseFailure) : RemoveUserFromWorldFailure
}

sealed interface UpdateWorldPermissionAuditInfoFailure : AddWorldParticipantStepFailure, RemoveUserAssignmentsInWorldFailure, RemoveUserFromWorldFailure {
    data class Other(val failure: DatabaseFailure) : UpdateWorldPermissionAuditInfoFailure
}

sealed interface VerifyParticipantAdderIsAdminFailure : AddWorldParticipantFailure, RemoveWorldParticipantFailure,
    GetWorldParticipantsFailure {
    data object NotAdmin : VerifyParticipantAdderIsAdminFailure
    data class Other(val failure: DatabaseFailure) : VerifyParticipantAdderIsAdminFailure
}

sealed interface VerifyUserInWorldFailure : RemoveWorldParticipantFailure {
    data object NotPresent : VerifyUserInWorldFailure
    data class Other(val failure: DatabaseFailure) : VerifyUserInWorldFailure
}

sealed interface VerifyUserExistsStepFailure : AddWorldParticipantFailure {
    data object UserDoesNotExist : VerifyUserExistsStepFailure
    data class Other(val failure: DatabaseFailure) : VerifyUserExistsStepFailure
}

sealed interface VerifyUserNotInWorldStepFailure : AddWorldParticipantFailure {
    data object UserAlreadyExists : VerifyUserNotInWorldStepFailure
    data class Other(val failure: DatabaseFailure) : VerifyUserNotInWorldStepFailure
}