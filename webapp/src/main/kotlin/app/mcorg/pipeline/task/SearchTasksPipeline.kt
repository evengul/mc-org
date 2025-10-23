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

object SearchTasksStep : Step<SearchTasksInput, SearchTasksFailures, SearchTasksResult> {
    override suspend fun process(input: SearchTasksInput): Result<SearchTasksFailures, SearchTasksResult> {
        val sortBy = when(input.sortBy) {
            "name_asc" -> "tc.name ASC, tc.priority_order ASC, tc.updated_at DESC"
            "lastModified_desc" -> "tc.updated_at DESC, tc.priority_order ASC, tc.name ASC"
            "priority_asc" -> "tc.priority_order ASC, tc.updated_at DESC, tc.name ASC"
            else -> "tc.priority_order ASC, tc.updated_at DESC, tc.name ASC"
        }
        val taskStep = DatabaseSteps.query<SearchTasksInput, SearchTasksFailures, List<Task>>(
            sql = SafeSQL.with("""
                WITH task_completion AS (
                    SELECT 
                        t.id,
                        t.project_id,
                        t.name,
                        t.description,
                        t.stage,
                        t.priority,
                        t.updated_at,
                        CASE 
                            WHEN t.priority = 'CRITICAL' THEN 1
                            WHEN t.priority = 'HIGH' THEN 2
                            WHEN t.priority = 'MEDIUM' THEN 3
                            WHEN t.priority = 'LOW' THEN 4
                            ELSE 5
                        END as priority_order,
                        CASE 
                            WHEN COUNT(tr.id) = 0 THEN FALSE
                            WHEN COUNT(tr.id) = COUNT(CASE 
                                WHEN tr.type = 'ACTION' AND tr.completed = TRUE THEN 1
                                WHEN tr.type = 'ITEM' AND tr.collected >= tr.required_amount THEN 1
                                ELSE NULL
                            END) THEN TRUE
                            ELSE FALSE
                        END as is_completed
                    FROM tasks t
                    LEFT JOIN task_requirements tr ON t.id = tr.task_id
                    WHERE t.project_id = ?
                      AND (? IS NULL OR LOWER(t.name) LIKE ? OR LOWER(t.description) LIKE ?)
                      AND (? = 'ALL' OR t.priority = ?)
                      AND (? = 'ALL' OR t.stage = ?)
                    GROUP BY t.id, t.project_id, t.name, t.description, t.stage, t.priority
                )
                SELECT tc.id, tc.project_id, tc.name, tc.description, tc.stage, tc.priority,
                       tr.id as req_id, tr.task_id, tr.type, tr.action, tr.item, 
                       tr.required_amount, tr.collected, tr.completed
                FROM task_completion tc
                LEFT JOIN task_requirements tr ON tc.id = tr.task_id
                WHERE (? = 'ALL' OR (? = 'IN_PROGRESS' AND tc.is_completed = FALSE) OR (? = 'COMPLETED' AND tc.is_completed = TRUE))
                ORDER BY $sortBy
            """.trimIndent()),
            parameterSetter = { statement, searchInput ->
                statement.setInt(1, searchInput.projectId)

                val searchPattern = searchInput.query?.let { "%$it%".lowercase() }
                statement.setString(2, searchInput.query)
                statement.setString(3, searchPattern)
                statement.setString(4, searchPattern)

                statement.setString(5, searchInput.priority)
                statement.setString(6, searchInput.priority)

                statement.setString(7, searchInput.stage)
                statement.setString(8, searchInput.stage)

                statement.setString(9, searchInput.completionStatus)
                statement.setString(10, searchInput.completionStatus)
                statement.setString(11, searchInput.completionStatus)
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
    val taskOrder = mutableListOf<Int>()
    val taskDetails = mutableMapOf<Int, TaskData>()
    val taskMap = mutableMapOf<Int, MutableList<TaskRequirement>>()

    while (resultSet.next()) {
        val taskId = resultSet.getInt("id")

        // Build task details map and preserve order
        if (!taskDetails.containsKey(taskId)) {
            taskOrder.add(taskId)
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
                    completed = resultSet.getBoolean("completed")
                )
                else -> throw IllegalStateException("Unknown requirement type: ${resultSet.getString("type")}")
            }
            taskMap[taskId]?.add(requirement)
        }
    }

    // Convert to Task objects in the original order from the query
    return taskOrder.mapNotNull { taskId ->
        taskDetails[taskId]?.let { taskData ->
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
}

private data class TaskData(
    val id: Int,
    val projectId: Int,
    val name: String,
    val description: String,
    val stage: ProjectStage,
    val priority: Priority
)
