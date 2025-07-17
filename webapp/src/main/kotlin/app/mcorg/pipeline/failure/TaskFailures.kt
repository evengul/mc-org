package app.mcorg.pipeline.failure

import app.mcorg.domain.model.task.PremadeTask
import app.mcorg.pipeline.DatabaseFailure

sealed interface TaskParamFailure
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

sealed interface AssignTaskStepFailure : AssignTaskFailure {
    data class Other(val failure: DatabaseFailure) : AssignTaskStepFailure
}

sealed interface ConvertMaterialListStepFailure : CreateLitematicaTasksFailure

sealed interface CreateCountableStepFailure : CreateCountableTaskFailure {
    data class Other(val failure: DatabaseFailure) : CreateCountableStepFailure
}

sealed interface CreateDoableTaskStepFailure : CreateDoableTaskFailure {
    data class Other(val failure: DatabaseFailure) : CreateDoableTaskStepFailure
}

sealed interface CreateLitematicaTasksStepFailure : CreateLitematicaTasksFailure {
    data class ItemFailure(
        val errors: List<Pair<PremadeTask, DatabaseFailure>>
    ) : CreateLitematicaTasksStepFailure
}

sealed interface DeleteTaskStepFailure : DeleteTaskFailure {
    data class Other(val failure: DatabaseFailure) : DeleteTaskStepFailure
}

sealed interface EnsureTaskExistsInProjectFailure : TaskParamFailure {
    data object TaskNotFound : EnsureTaskExistsInProjectFailure
    data class Other(val failure: DatabaseFailure) : EnsureTaskExistsInProjectFailure
}

sealed interface GetCountableTaskDoneMoreInputFailure : EditDoneMoreTaskFailure {
    data object MissingDone : GetCountableTaskDoneMoreInputFailure
}

sealed interface GetCountableTaskInputStepFailure : CreateCountableTaskFailure {
    data class MultipleMissing(val missing: List<GetCountableTaskInputStepFailure>) : GetCountableTaskInputStepFailure
    data object NameNotPresent : GetCountableTaskInputStepFailure
    data object AmountNotPresent : GetCountableTaskInputStepFailure
}

sealed interface GetCountableTasksEditInputFailure : UpdateTaskRequirementsFailure {
    data object TaskIdNotPresent : GetCountableTasksEditInputFailure
    data object RequirementsNotPresent : GetCountableTasksEditInputFailure
    data object DoneNotPresent : GetCountableTasksEditInputFailure
    data class MultipleMissing(val missing: Set<GetCountableTasksEditInputFailure>) : GetCountableTasksEditInputFailure
}

sealed interface GetDoableTaskInputFailure : CreateDoableTaskFailure {
    data object MissingName : GetDoableTaskInputFailure
}

sealed interface GetLitematicaTasksInputStepFailure : CreateLitematicaTasksFailure {
    data object NoFile : GetLitematicaTasksInputStepFailure
}

sealed interface GetTaskIdParameterFailure : TaskParamFailure {
    data object TaskIdNotPresent : GetTaskIdParameterFailure
}

sealed interface GetTaskStageInputFailure : UpdateTaskStageFailure {
    data object MissingStage : GetTaskStageInputFailure
    data object InvalidStage : GetTaskStageInputFailure
}

sealed interface RemoveTaskAssignmentStepFailure : AssignTaskFailure {
    data class Other(val failure: DatabaseFailure) : RemoveTaskAssignmentStepFailure
}

sealed interface TaskValidationFailure : CreateDoableTaskFailure, CreateCountableTaskFailure,
    CreateLitematicaTasksFailure, EditDoneMoreTaskFailure {
    data object TooShortName : TaskValidationFailure
    data object ShouldBePositive : TaskValidationFailure
    data class Multiple(val failures: List<Pair<PremadeTask, TaskValidationFailure>>) : TaskValidationFailure
}

sealed interface UpdateCountableTaskDoneFailure : EditDoneMoreTaskFailure {
    data class Other(val failure: DatabaseFailure) : UpdateCountableTaskDoneFailure
}

sealed interface UpdateCountableTaskRequirementsStepFailure : UpdateTaskRequirementsFailure {
    data class Other(val failure: DatabaseFailure) : UpdateCountableTaskRequirementsStepFailure
}

sealed interface UpdateTaskAuditInfoFailure : EditDoneMoreTaskFailure, UpdateTaskRequirementsFailure, AssignTaskFailure,
    UpdateTaskStageFailure {
    data class Other(val failure: DatabaseFailure) : UpdateTaskAuditInfoFailure
}

sealed interface UpdateTaskProjectAuditInfoFailure : DeleteTaskFailure, CreateCountableTaskFailure,
    CreateDoableTaskFailure, CreateLitematicaTasksFailure {
    data class Other(val failure: DatabaseFailure) : UpdateTaskProjectAuditInfoFailure
}

sealed interface UpdateTaskStageStepFailure : UpdateTaskStageFailure {
    data class Other(val failure: DatabaseFailure) : UpdateTaskStageStepFailure
}

sealed interface ValidateMaterialListStepFailure : CreateLitematicaTasksFailure {
    data object InvalidFile : ValidateMaterialListStepFailure
    data object BlankFile : ValidateMaterialListStepFailure
}