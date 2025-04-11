package app.mcorg.domain.model.projects

import app.mcorg.domain.model.task.TaskSorters
import app.mcorg.domain.model.task.TaskSpecification
import app.mcorg.domain.model.task.matches

fun SlimProject.matches(specification: ProjectSpecification): Boolean {
    if (specification.search != null && !name.contains(specification.search, ignoreCase = true)) {
        return false
    }
    if (specification.hideCompleted && this.progress >= 1.0) {
        return false
    }
    return true
}

fun Project.filterSortTasks(specification: TaskSpecification, userId: Int): Project {
    val sorter = when(specification.sortBy ?: "DONE") {
        "DONE" -> TaskSorters::byCompletion
        "ASSIGNEE" -> TaskSorters::byAssignee
        else -> TaskSorters::byName
    }
    val tasks = tasks.filter { it.matches(specification, userId) }.sortedWith(sorter).toMutableList()

    return copy(tasks = tasks)
}