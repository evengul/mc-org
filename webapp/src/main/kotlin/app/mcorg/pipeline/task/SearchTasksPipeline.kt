package app.mcorg.pipeline.task

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.task.ActionRequirement
import app.mcorg.domain.model.task.ItemRequirement
import app.mcorg.domain.model.task.Priority
import app.mcorg.domain.model.task.Task
import app.mcorg.domain.model.task.TaskRequirement
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure
import io.ktor.http.*
import java.sql.ResultSet

data class SearchTasksInput(
    val projectId: Int,
    val userId: Int,
    val query: String? = null,
    val completionStatus: String = "IN_PROGRESS", // ALL, IN_PROGRESS, COMPLETED
    val priority: String = "ALL" // ALL or Priority enum name
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
                priority = priority
            )
        )
    }
}

object InjectSearchTasksContextStep : Step<SearchTasksInput, SearchTasksFailures, SearchTasksInput> {
    override suspend fun process(input: SearchTasksInput): Result<SearchTasksFailures, SearchTasksInput> {
        return Result.Success(input)
    }
}

object ValidateSearchTasksAccessStep : Step<SearchTasksInput, SearchTasksFailures, SearchTasksInput> {
    override suspend fun process(input: SearchTasksInput): Result<SearchTasksFailures, SearchTasksInput> {
        // Since the requirement specifies no access validation, we pass through
        return Result.Success(input)
    }
}

object SearchTasksStep : Step<SearchTasksInput, SearchTasksFailures, SearchTasksResult> {
    override suspend fun process(input: SearchTasksInput): Result<SearchTasksFailures, SearchTasksResult> {
        val taskStep = DatabaseSteps.query<SearchTasksInput, SearchTasksFailures, List<Task>>(
            sql = SafeSQL.select("""
                SELECT t.id, t.project_id, t.name, t.description, t.stage, t.priority,
                       tr.id as req_id, tr.task_id, tr.type, tr.action, tr.item, 
                       tr.required_amount, tr.collected
                FROM tasks t
                LEFT JOIN task_requirements tr ON t.id = tr.task_id
                WHERE t.project_id = ?
                  AND (? IS NULL OR LOWER(t.name) LIKE LOWER(?) OR LOWER(t.description) LIKE LOWER(?))
                  AND (? = 'ALL' OR (? = 'IN_PROGRESS' AND t.stage != 'COMPLETED') OR (? = 'COMPLETED' AND t.stage = 'COMPLETED'))
                  AND (? = 'ALL' OR t.priority = ?)
                ORDER BY t.id, tr.id
            """.trimIndent()),
            parameterSetter = { statement, searchInput ->
                statement.setInt(1, searchInput.projectId)

                val searchPattern = searchInput.query?.let { "%$it%" }
                statement.setString(2, searchInput.query)
                statement.setString(3, searchPattern)
                statement.setString(4, searchPattern)

                statement.setString(5, searchInput.completionStatus)
                statement.setString(6, searchInput.completionStatus)
                statement.setString(7, searchInput.completionStatus)

                statement.setString(8, searchInput.priority)
                statement.setString(9, searchInput.priority)
            },
            errorMapper = { _: DatabaseFailure -> SearchTasksFailures.DatabaseError },
            resultMapper = { resultSet -> extractTasksFromResultSet(resultSet) }
        )

        return when (val result = taskStep.process(input)) {
            is Result.Success -> Result.Success(SearchTasksResult(result.value))
            is Result.Failure -> Result.Failure(result.error)
        }
    }
}

private fun extractTasksFromResultSet(resultSet: ResultSet): List<Task> {
    val taskDetails = mutableMapOf<Int, TaskData>()
    val taskMap = mutableMapOf<Int, MutableList<TaskRequirement>>()

    while (resultSet.next()) {
        val taskId = resultSet.getInt("id")

        // Build task details map
        if (!taskDetails.containsKey(taskId)) {
            taskDetails[taskId] = TaskData(
                id = taskId,
                projectId = resultSet.getInt("project_id"),
                name = resultSet.getString("name"),
                description = resultSet.getString("description"),
                stage = ProjectStage.valueOf(resultSet.getString("stage")),
                priority = Priority.valueOf(resultSet.getString("priority"))
            )
            taskMap[taskId] = mutableListOf()
        }

        // Add requirements if they exist
        val reqId = resultSet.getInt("req_id")
        if (reqId > 0) {
            val requirement = when (resultSet.getString("type")) {
                "ITEM" -> ItemRequirement(
                    id = reqId,
                    item = resultSet.getString("item"),
                    requiredAmount = resultSet.getInt("required_amount"),
                    collected = resultSet.getInt("collected")
                )
                "ACTION" -> ActionRequirement(
                    id = reqId,
                    action = resultSet.getString("action"),
                    completed = resultSet.getBoolean("collected") // Using collected field to store completion status for actions
                )
                else -> throw IllegalStateException("Unknown requirement type: ${resultSet.getString("type")}")
            }
            taskMap[taskId]?.add(requirement)
        }
    }

    // Convert to Task objects
    return taskDetails.values.map { taskData ->
        Task(
            id = taskData.id,
            projectId = taskData.projectId,
            name = taskData.name,
            description = taskData.description,
            stage = taskData.stage,
            priority = taskData.priority,
            requirements = taskMap[taskData.id] ?: emptyList()
        )
    }
}

private data class TaskData(
    val id: Int,
    val projectId: Int,
    val name: String,
    val description: String,
    val stage: ProjectStage,
    val priority: Priority
)
