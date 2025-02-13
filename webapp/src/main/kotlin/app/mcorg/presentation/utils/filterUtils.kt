package app.mcorg.presentation.utils

import app.mcorg.domain.*
import app.mcorg.presentation.entities.ProjectFiltersRequest
import app.mcorg.presentation.entities.TaskFiltersRequest
import io.ktor.http.*
import java.net.URLDecoder

fun createProjectFilters(currentUrl: String?): ProjectFiltersRequest {
    val queries = queriesToMap(currentUrl)
    return ProjectFiltersRequest(
        search = queries["search"],
        hideCompleted = queries["hideCompleted"] == "on"
    )
}

fun createTaskFilters(currentUrl: String?): TaskFiltersRequest {
    val queries = queriesToMap(currentUrl)
    return TaskFiltersRequest(
        search = queries["search"],
        sortBy = queries["sortBy"],
        assigneeFilter = queries["assigneeFilter"],
        completionFilter = queries["completionFilter"],
        taskTypeFilter = queries["taskTypeFilter"],
        amountFilter = queries["amountFilter"]?.toIntOrNull()
    )
}

fun SlimProject.allowedByFilter(filters: ProjectFiltersRequest): Boolean {
    if (filters.hideCompleted && this.progress >= 1.0) return false
    if (filters.search?.isNotBlank() == true) {
        val search = filters.search
        val username = assignee?.username?.lowercase()
        val name = name.lowercase()
        if (username == null) {
            if (!name.lowercase().contains(search.lowercase())) {
                return false
            }
        } else {
            if (!username.lowercase().contains(search.lowercase()) && !name.lowercase().contains(search.lowercase())) {
                return false
            }
        }
    }

    return true
}

fun filterAndSortProject(userId: Int, project: Project, filters: TaskFiltersRequest): Project {
    val tasks = project.tasks.filter {
        val search = filters.search
        if (!search.isNullOrBlank()) {
            if ((it.assignee != null && it.assignee.username.lowercase().contains(search.lowercase())) || (!it.name.lowercase().contains(search.lowercase()))) {
                return@filter false
            }
        }

        val assigneeFilter = filters.assigneeFilter
        if (!assigneeFilter.isNullOrBlank()) {
            if ((assigneeFilter == "UNASSIGNED" && it.assignee != null)
                || (assigneeFilter == "MINE" && (it.assignee == null || it.assignee.id != userId))
                || (assigneeFilter.toIntOrNull() != null && (it.assignee == null || it.assignee.id.toString() != assigneeFilter))) {
                return@filter false
            }
        }

        val completionFilter = filters.completionFilter
        if (!completionFilter.isNullOrBlank()) {
            if ((completionFilter == "NOT_STARTED" && it.done > 0) ||
                (completionFilter == "IN_PROGRESS" && (it.done == 0 || it.isDone())) ||
                (completionFilter == "COMPLETE" && !it.isDone())) {
                return@filter false
            }
        }

        val typeFilter = filters.taskTypeFilter
        if (!typeFilter.isNullOrBlank()) {
            if ((typeFilter == "DOABLE" && it.isCountable()) ||
                (typeFilter == "COUNTABLE") && !it.isCountable()) {
                return@filter false
            }
        }

        val amountFilter = filters.amountFilter
        if (amountFilter != null) {
            if ((it.isCountable() && it.done < amountFilter) || (!it.isCountable() && amountFilter > 1)) {
                return@filter false
            }
        }

        return@filter true
    }

    val sortBy = filters.sortBy ?: "DONE"

    val sortedTasks = when(sortBy) {
        "DONE" -> tasks.sortedWith(::sortProjectsByCompletion)
        "ASSIGNEE" -> tasks.sortedByDescending { it.assignee?.username }
        else -> tasks.sortedBy { it.name }
    }

    return project.copy(tasks = sortedTasks.toMutableList())
}

private fun sortProjectsByCompletion(a: Task, b: Task): Int {
    if (a.isDone()) {
        if (b.isDone()) {
            return a.name.compareTo(b.name)
        }
        return 1
    } else if(b.isDone()) {
        return -1
    }
    if (a.done == b.done && a.needed == b.needed) return a.name.compareTo(b.name)
    if (a.needed == b.needed) return b.done - a.done
    return b.needed - a.needed
}

private fun queriesToMap(currentUrl: String?): Map<String, String?> {
    if (currentUrl == null) return emptyMap()
    if (currentUrl.contains("?")) {
        return Url(currentUrl).encodedQuery.split("&")
            .map { URLDecoder.decode(it, "UTF-8") }
            .associate { parseQuery(it) }
    }
    return emptyMap()
}

private fun parseQuery(query: String): Pair<String, String?> {
    if (query.contains("=")) {
        val split = query.split("=")
        return split[0] to split[1]
    }
    return query to null
}