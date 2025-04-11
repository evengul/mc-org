package app.mcorg.presentation.utils.url

import app.mcorg.domain.model.task.TaskSpecification

fun URLMappers.Companion.taskFilterURLMapper(url: String?): TaskSpecification {
    val queries = queriesToMap(url)
    return TaskSpecification(
        search = queries["search"],
        sortBy = queries["sortBy"],
        assigneeFilter = queries["assigneeFilter"],
        completionFilter = queries["completionFilter"],
        taskTypeFilter = queries["taskTypeFilter"],
        amountFilter = queries["amountFilter"]?.toIntOrNull()
    )
}