package app.mcorg.presentation.entities

import app.mcorg.domain.Dimension
import app.mcorg.domain.Priority

data class CreateWorldRequest(val worldName: String)
data class AddUserRequest(val username: String)
data class CreateProjectRequest(
    val name: String,
    val priority: Priority,
    val dimension: Dimension,
    val requiresPerimeter: Boolean
)
data class AddDoableRequest(val taskName: String)
data class AddCountableRequest(val taskName: String, val needed: Int)
data class EditCountableRequest(val id: Int, val needed: Int, val done: Int)
data class TaskFiltersRequest(val search: String?,
                                val sortBy: String?,
                                val assigneeFilter: String?,
                                val completionFilter: String?,
                                val taskTypeFilter: String?)