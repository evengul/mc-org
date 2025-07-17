package app.mcorg.pipeline.failure

import app.mcorg.pipeline.DatabaseFailure

sealed interface WorldParamFailure {
    data object UserNotInWorld : WorldParamFailure
    data class Other(val failure: DatabaseFailure) : WorldParamFailure
}

sealed interface GetAllWorldsFailure

sealed interface CreateWorldFailure

sealed interface DeleteWorldFailure

sealed interface SelectWorldFailure : CreateWorldFailure

sealed interface CreateWorldPermissionFailure : CreateWorldFailure {
    data class Other(val failure: DatabaseFailure) : CreateWorldPermissionFailure
}

sealed interface CreateWorldStepFailure : CreateWorldFailure {
    data class Other(val failure: DatabaseFailure) : CreateWorldStepFailure
}

sealed interface DeleteWorldStepFailure : DeleteWorldFailure {
    data class Other(val failure: DatabaseFailure) : DeleteWorldStepFailure
}

sealed interface EnsureWorldExistsFailure : WorldParamFailure {
    data object WorldNotFound : EnsureWorldExistsFailure
    data class Other(val failure: DatabaseFailure) : EnsureWorldExistsFailure
}

sealed interface GetAllPermittedWorldsForUserFailure : GetAllWorldsFailure {
    data object InvalidAuthority : GetAllPermittedWorldsForUserFailure
    data class Other(val failure: DatabaseFailure) : GetAllPermittedWorldsForUserFailure
}

sealed interface GetSelectedWorldIdFailure : GetAllWorldsFailure {
    data object NoWorldSelected : GetSelectedWorldIdFailure
    data class Other(val failure: DatabaseFailure) : GetSelectedWorldIdFailure
}

sealed interface GetWorldIdParameterFailure : WorldParamFailure {
    data object WorldIdNotPresent : GetWorldIdParameterFailure
}

sealed interface GetWorldNameFailure : CreateWorldFailure {
    data object NotPresent : GetWorldNameFailure
}

sealed interface GetWorldSelectionValueFailure : SelectWorldFailure {
    data object NotFound : GetWorldSelectionValueFailure
    data object NotInteger : GetWorldSelectionValueFailure
}

sealed interface RemoveWorldPermissionsForAllUsersStepFailure : DeleteWorldFailure {
    data class Other(val failure: DatabaseFailure) : RemoveWorldPermissionsForAllUsersStepFailure
}

sealed interface SelectWorldStepFailure : SelectWorldFailure, CreateWorldFailure {
    data class Other(val failure: DatabaseFailure) : SelectWorldFailure
}

sealed interface UnSelectWorldForAllUsersStepFailure : DeleteWorldFailure {
    data class Other(val failure: DatabaseFailure) : UnSelectWorldForAllUsersStepFailure
}

sealed interface ValidateAvailableWorldNameFailure : CreateWorldFailure {
    data object AlreadyExists : ValidateAvailableWorldNameFailure
    data class Other(val failure: DatabaseFailure) : ValidateAvailableWorldNameFailure
}

sealed interface WorldValidationFailure : CreateWorldFailure {
    data object WorldNameEmpty : WorldValidationFailure
    data object WorldNameTooLong : WorldValidationFailure
}