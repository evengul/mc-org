package app.mcorg.presentation.utils

import app.mcorg.domain.model.projects.*
import app.mcorg.domain.model.task.TaskSorters
import app.mcorg.domain.model.task.TaskSpecification
import app.mcorg.domain.model.task.matches

fun filterAndSortProject(userId: Int, project: Project, filters: TaskSpecification): Project {
    val tasks = project.tasks.filter { it.matches(filters, userId) }

    val sortBy = filters.sortBy ?: "DONE"

    val sortedTasks = when(sortBy) {
        "DONE" -> tasks.sortedWith(TaskSorters::byCompletion)
        "ASSIGNEE" -> tasks.sortedWith(TaskSorters::byAssignee)
        else -> tasks.sortedWith(TaskSorters::byName)
    }

    return project.copy(tasks = sortedTasks.toMutableList())
}