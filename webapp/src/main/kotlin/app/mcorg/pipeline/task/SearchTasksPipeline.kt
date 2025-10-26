package app.mcorg.pipeline.task

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.task.Priority
import app.mcorg.domain.model.task.Task
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import io.ktor.http.*

data class SearchTasksInput(
    val projectId: Int,
    val userId: Int,
    val query: String? = null,
    val completionStatus: String = "IN_PROGRESS", // ALL, IN_PROGRESS, COMPLETED
    val priority: String = "ALL", // ALL or Priority enum name
    val stage: String = "ALL",
    val sortBy: String = "lastModified_desc" // name_asc, lastModified_desc
)

data class SearchTasksResult(
    val tasks: List<Task>
)

sealed class SearchTasksFailures {
    data class ValidationError(val errors: List<String>) : SearchTasksFailures()
    object ProjectNotFound : SearchTasksFailures()
    object InsufficientPermissions : SearchTasksFailures()
    object DatabaseError : SearchTasksFailures()
}

object ValidateSearchTasksInputStep : Step<Parameters, SearchTasksFailures, SearchTasksInput> {
    override suspend fun process(input: Parameters): Result<SearchTasksFailures, SearchTasksInput> {
        val errors = mutableListOf<String>()

        val projectIdParam = input["projectId"]
        val userIdParam = input["userId"]
        val query = input["query"]?.takeIf { it.isNotBlank() }
        val completionStatus = input["completionStatus"]?.takeIf { it in listOf("ALL", "IN_PROGRESS", "COMPLETED") } ?: "IN_PROGRESS"
        val priority = input["priority"]?.takeIf {
            it == "ALL" || Priority.entries.any { priority -> priority.name == it }
        } ?: "ALL"
        val stage = input["stage"]?.takeIf {
            it == "ALL" || ProjectStage.entries.any { stage -> stage.name == it }
        } ?: "ALL"

        val sortBy = input["sortBy"]?.takeIf { it.isNotBlank() && it in listOf("name_asc", "lastModified_desc", "priority_asc") } ?: "priority_desc"

        val projectId = projectIdParam?.toIntOrNull()
        val userId = userIdParam?.toIntOrNull()

        if (projectId == null) errors.add("Project ID is required")
        if (userId == null) errors.add("User ID is required")

        if (errors.isNotEmpty()) {
            return Result.Failure(SearchTasksFailures.ValidationError(errors))
        }

        return Result.Success(
            SearchTasksInput(
                projectId = projectId!!,
                userId = userId!!,
                query = query,
                completionStatus = completionStatus,
                priority = priority,
                stage = stage,
                sortBy = sortBy
            )
        )
    }
}

object InjectSearchTasksContextStep : Step<SearchTasksInput, SearchTasksFailures, SearchTasksInput> {
    override suspend fun process(input: SearchTasksInput): Result<SearchTasksFailures, SearchTasksInput> {
        return Result.Success(input)
    }
}