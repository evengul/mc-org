package app.mcorg.pipeline.task

import app.mcorg.pipeline.DatabaseFailure

sealed interface CreateDoableTaskFailure
sealed interface CreateCountableTaskFailure
sealed interface CreateLitematicaTasksFailure
sealed interface DeleteTaskFailure
sealed interface EditDoneMoreTaskFailure
sealed interface UpdateTaskRequirementsFailure
sealed interface AssignTaskFailure {
    data object UserIdNotPresent : AssignTaskFailure
    data object UserNotFound : AssignTaskFailure
    data class Other(val failure: DatabaseFailure) : AssignTaskFailure
}
sealed interface UpdateTaskStageFailure

sealed interface GetTasksFailure :
    CreateDoableTaskFailure,
    CreateCountableTaskFailure,
    CreateLitematicaTasksFailure,
    DeleteTaskFailure,
    EditDoneMoreTaskFailure,
    UpdateTaskRequirementsFailure,
    AssignTaskFailure {
    data object ProjectNotFound : GetTasksFailure
    data class Other(val failure: DatabaseFailure) : GetTasksFailure
}