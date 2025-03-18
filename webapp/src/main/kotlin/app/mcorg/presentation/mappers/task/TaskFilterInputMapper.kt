package app.mcorg.presentation.mappers.task

import app.mcorg.domain.model.task.TaskSpecification
import app.mcorg.presentation.mappers.InputMappers
import io.ktor.http.*

fun InputMappers.Companion.taskFilterInputMapper(parameters: Parameters) = TaskSpecification(
    parameters["search"],
    parameters["sortBy"],
    parameters["assigneeFilter"],
    parameters["completionFilter"],
    parameters["taskTypeFilter"],
    parameters["amountFilter"]?.toIntOrNull()
)