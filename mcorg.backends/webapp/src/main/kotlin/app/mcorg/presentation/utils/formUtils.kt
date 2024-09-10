package app.mcorg.presentation.utils

import app.mcorg.domain.Dimension
import app.mcorg.domain.PremadeTask
import app.mcorg.domain.Priority
import app.mcorg.domain.tasksFromMaterialList
import app.mcorg.presentation.entities.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*

suspend fun ApplicationCall.receiveCreateWorldRequest(): CreateWorldRequest {
    val data = receiveParameters()
    val worldName = data["worldName"] ?: throw IllegalArgumentException("worldName is required")
    if (worldName.length < 3) throw IllegalArgumentException("worldName must be longer than 3 characters")
    return CreateWorldRequest(worldName)
}

suspend fun ApplicationCall.receiveAddUserRequest(): AddUserRequest {
    val data = receiveParameters()
    val username = data["username"] ?: throw IllegalArgumentException("username is required")
    return AddUserRequest(username)
}

fun ApplicationCall.receiveAssignUserRequest(): AddUserRequest {
    val username = parameters["username"] ?: throw IllegalArgumentException("username is required")
    return AddUserRequest(username)
}

suspend fun ApplicationCall.receiveCreateProjectRequest(): CreateProjectRequest {
    val data = receiveParameters()
    val name = data["projectName"] ?: throw IllegalArgumentException("projectName is required")
    val dimension = data["dimension"]?.toDimension() ?: Dimension.OVERWORLD
    val priority = data["priority"]?.toPriority() ?: Priority.NONE
    val requiresPerimeter = data["requiresPerimeter"]?.toBooleanStrictOrNull() == true
    return CreateProjectRequest(name, priority, dimension, requiresPerimeter)
}

suspend fun ApplicationCall.receiveTaskFilters() = TaskFiltersRequest(
    parameters["search"],
    parameters["sortBy"],
    parameters["assigneeFilter"],
    parameters["completionFilter"],
    parameters["taskTypeFilter"]
)

suspend fun ApplicationCall.receiveDoableTaskRequest(): AddDoableRequest {
    val data = receiveParameters()
    val name = data["taskName"] ?: throw IllegalArgumentException("taskName is required")
    return AddDoableRequest(name)
}

suspend fun ApplicationCall.receiveCountableTaskRequest(): AddCountableRequest {
    val data = receiveParameters()
    val name = data["taskName"] ?: throw IllegalArgumentException("taskName is required")
    val amount = data["amount"]?.toIntOrNull() ?: throw IllegalArgumentException("amount is required")
    return AddCountableRequest(name, amount)
}

suspend fun ApplicationCall.getEditCountableTaskRequirements(): EditCountableRequest {
    val data = receiveParameters()
    val id = data["id"]?.toIntOrNull() ?: throw IllegalArgumentException("id is required")
    val done = data["done"]?.toIntOrNull() ?: throw IllegalArgumentException("done is required")
    val needed = data["needed"]?.toIntOrNull() ?: throw IllegalArgumentException("needed is required")

    if (done > needed) throw IllegalArgumentException("You cannot do more than you need")

    return EditCountableRequest(id = id, needed = needed, done = done)
}

suspend fun ApplicationCall.receiveMaterialListTasks(): List<PremadeTask> {
    val data = receiveMultipart()
    val file = data.readAllParts().find { it.name == "file" } as PartData.FileItem?
    return file?.streamProvider?.let {
        it().tasksFromMaterialList()
    } ?: emptyList()
}

private fun String?.toDimension(): Dimension = when(this) {
    "OVERWORLD" -> Dimension.OVERWORLD
    "NETHER" -> Dimension.NETHER
    "THE_END" -> Dimension.THE_END
    else -> Dimension.OVERWORLD
}

private fun String?.toPriority(): Priority = when(this) {
    "LOW" -> Priority.LOW
    "MEDIUM" -> Priority.MEDIUM
    "HIGH" -> Priority.HIGH
    else -> Priority.NONE
}