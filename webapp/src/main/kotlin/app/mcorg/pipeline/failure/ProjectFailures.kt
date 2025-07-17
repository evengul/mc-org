package app.mcorg.pipeline.failure

import app.mcorg.pipeline.DatabaseFailure

sealed interface ProjectParamFailure
sealed interface GetProjectsFailure
sealed interface GetProjectFailure
sealed interface CreateProjectFailure
sealed interface DeleteProjectFailure
sealed interface AssignProjectFailure

sealed interface AssignUserToProjectStepFailure : AssignProjectFailure {
    data class Other(val failure: DatabaseFailure) : AssignUserToProjectStepFailure
}

sealed interface CreateProjectStepFailure : CreateProjectFailure {
    data class Other(val failure: DatabaseFailure) : CreateProjectStepFailure
}

sealed interface DeleteProjectStepFailure : DeleteProjectFailure {
    data class Other(val failure: DatabaseFailure) : DeleteProjectStepFailure
}

sealed interface EnsureProjectExistsInWorldFailure : ProjectParamFailure {
    data object ProjectNotFound : EnsureProjectExistsInWorldFailure
    data class Other(val failure: DatabaseFailure) : EnsureProjectExistsInWorldFailure
}

sealed interface EnsureUserExistsInProjectFailure : AssignProjectFailure {
    data object UserNotFound : EnsureUserExistsInProjectFailure
    data class Other(val failure: DatabaseFailure) : EnsureUserExistsInProjectFailure
}

sealed interface GetCreateProjectInputFailure : CreateProjectFailure {
    data object NameMissing : GetCreateProjectInputFailure
}

sealed interface GetProfileForProjectsFailure : GetProjectsFailure {
    data object UserNotFound : GetProfileForProjectsFailure
    data class Other(val failure: DatabaseFailure) : GetProfileForProjectsFailure
}

sealed interface GetProjectAssignmentInputStepFailure : AssignProjectFailure {
    data object UserIdNotPresent : GetProjectAssignmentInputStepFailure
}

sealed interface GetProjectCountWithFilteredCountFailure : CreateProjectFailure, DeleteProjectFailure,
    AssignProjectFailure {
    data class Other(val failure: DatabaseFailure) : GetProjectCountWithFilteredCountFailure
}

sealed interface GetProjectIdParamFailure : ProjectParamFailure {
    data object ProjectIdNotPresent : GetProjectIdParamFailure
}

sealed interface GetProjectSpecificationStepFailure : GetProjectsFailure

sealed interface GetProjectStepFailure : GetProjectFailure, CreateProjectFailure, AssignProjectFailure {
    data object NotFound : GetProjectStepFailure
    data class Other(val failure: DatabaseFailure) : GetProjectStepFailure
}

sealed interface GetSpecifiedProjectsStepFailure : GetProjectsFailure {
    data class Other(val failure: DatabaseFailure) : GetSpecifiedProjectsStepFailure
}

sealed interface GetWorldUsersForProjectsFailure : GetProjectsFailure, CreateProjectFailure, GetProjectFailure,
    AssignProjectFailure {
    data class Other(val failure: DatabaseFailure) : GetWorldUsersForProjectsFailure
}

sealed interface RemoveProjectAssignmentStepFailure : AssignProjectFailure {
    data class Other(val failure: DatabaseFailure) : RemoveProjectAssignmentStepFailure
}

sealed interface UpdateProjectAuditInfoFailure : AssignProjectFailure {
    data class Other(val failure: DatabaseFailure) : UpdateProjectAuditInfoFailure
}

sealed interface ValidateCreateProjectInputFailure : CreateProjectFailure {
    data object NameAlreadyExistsInWorld : ValidateCreateProjectInputFailure
    data class Other(val failure: DatabaseFailure) : ValidateCreateProjectInputFailure
}

