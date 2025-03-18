package app.mcorg.presentation.mappers.task

import app.mcorg.domain.model.task.TaskSpecification
import app.mcorg.presentation.mappers.URLMappers

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