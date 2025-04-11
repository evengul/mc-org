package app.mcorg.domain.model.task

data class TaskSpecification(val search: String?,
                             val sortBy: String?,
                             val assigneeFilter: String?,
                             val completionFilter: String?,
                             val taskTypeFilter: String?,
                             val amountFilter: Int?) {
    companion object {
        fun default(): TaskSpecification {
            return TaskSpecification(
                search = null,
                sortBy = null,
                assigneeFilter = null,
                completionFilter = null,
                taskTypeFilter = null,
                amountFilter = null
            )
        }
    }
}