package app.mcorg.domain.model.task

data class TaskSpecification(val search: String?,
                             val sortBy: String?,
                             val assigneeFilter: String?,
                             val completionFilter: String?,
                             val taskTypeFilter: String?,
                             val amountFilter: Int?)