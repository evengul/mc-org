package app.mcorg.domain.model.task

fun Task.matches(specification: TaskSpecification, userId: Int): Boolean {
    val search = specification.search
    if (!search.isNullOrBlank()) {
        if ((assignee != null && assignee.username.lowercase().contains(search.lowercase())) || (!name.lowercase().contains(search.lowercase()))) {
            return false
        }
    }

    val assigneeFilter = specification.assigneeFilter
    if (!assigneeFilter.isNullOrBlank()) {
        if ((assigneeFilter == "UNASSIGNED" && assignee != null)
            || (assigneeFilter == "MINE" && (assignee == null || assignee.id != userId))
            || (assigneeFilter.toIntOrNull() != null && (assignee == null || assignee.id.toString() != assigneeFilter))) {
            return false
        }
    }

    val completionFilter = specification.completionFilter
    if (!completionFilter.isNullOrBlank()) {
        if ((completionFilter == "NOT_STARTED" && done > 0) ||
            (completionFilter == "IN_PROGRESS" && (done == 0 || isDone())) ||
            (completionFilter == "COMPLETE" && !isDone())) {
            return false
        }
    }

    val typeFilter = specification.taskTypeFilter
    if (!typeFilter.isNullOrBlank()) {
        if ((typeFilter == "DOABLE" && isCountable()) ||
            (typeFilter == "COUNTABLE") && !isCountable()) {
            return false
        }
    }

    val amountFilter = specification.amountFilter
    if (amountFilter != null) {
        if ((isCountable() && done < amountFilter) || (!isCountable() && amountFilter > 1)) {
            return false
        }
    }

    return true
}