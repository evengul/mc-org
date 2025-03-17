package app.mcorg.presentation.entities.task

data class AddCountableRequest(val taskName: String, val needed: Int)
data class EditCountableRequest(val id: Int, val needed: Int, val done: Int)
data class UpdateStageRequest(val stage: String)
data class TaskFiltersRequest(val search: String?,
                              val sortBy: String?,
                              val assigneeFilter: String?,
                              val completionFilter: String?,
                              val taskTypeFilter: String?,
                              val amountFilter: Int?)