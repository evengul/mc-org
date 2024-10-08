package app.mcorg.presentation.entities

import app.mcorg.domain.*

data class CreateWorldRequest(val worldName: String, val gameType: GameType, val version: WorldVersion, val isTechnical: Boolean)
data class AddUserRequest(val username: String)
sealed interface AssignUserOrDeleteAssignmentRequest
data class AssignUserRequest(val userId: Int) : AssignUserOrDeleteAssignmentRequest
data object DeleteAssignmentRequest : AssignUserOrDeleteAssignmentRequest
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
                                val taskTypeFilter: String?,
                                val amountFilter: Int?)

data class ProjectFiltersRequest(val search: String?, val hideCompleted: Boolean)

data class ContraptionRequest(
    val name: String,
    val description: String?,
    val authors: List<String>,
    val gameType: GameType,
    val version: ContraptionVersion,
    val schematicUrl: String?,
    val worldDownloadUrl: String?
)