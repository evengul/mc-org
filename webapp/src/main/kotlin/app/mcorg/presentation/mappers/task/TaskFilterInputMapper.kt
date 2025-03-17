package app.mcorg.presentation.mappers.task

import app.mcorg.presentation.entities.task.TaskFiltersRequest
import app.mcorg.presentation.mappers.InputMappers
import io.ktor.http.*

fun InputMappers.Companion.taskFilterInputMapper(parameters: Parameters) = TaskFiltersRequest(
    parameters["search"],
    parameters["sortBy"],
    parameters["assigneeFilter"],
    parameters["completionFilter"],
    parameters["taskTypeFilter"],
    parameters["amountFilter"]?.toIntOrNull()
)