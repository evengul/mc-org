package app.mcorg.pipeline.project

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

data class GetTasksByProjectIdInput(
    val projectId: Int,
    val includeCompleted: Boolean = false
)

sealed interface GetTasksByProjectIdFailures {
    data object DatabaseError : GetTasksByProjectIdFailures
}

object GetTasksByProjectIdStep : Step<GetTasksByProjectIdInput, GetTasksByProjectIdFailures, List<Task>> {
    override suspend fun process(input: GetTasksByProjectIdInput): Result<GetTasksByProjectIdFailures, List<Task>> {
        // First fetch all tasks for the project
        val tasksStep = DatabaseSteps.query<GetTasksByProjectIdInput, GetTasksByProjectIdFailures, List<Task>>(
            sql = SafeSQL.select("""
                SELECT 
                    t.id,
                    t.project_id,
                    t.name,
                    t.description,
                    t.stage,
                    t.priority,
                    tr.id as req_id,
                    tr.type as req_type,
                    tr.item,
                    tr.required_amount,
                    tr.collected,
                    tr.action,
                    tr.completed,
                    CASE 
                        WHEN t.priority = 'CRITICAL' THEN 1
                        WHEN t.priority = 'HIGH' THEN 2
                        WHEN t.priority = 'MEDIUM' THEN 3
                        WHEN t.priority = 'LOW' THEN 4
                        ELSE 5
                    END as priority_order
                FROM tasks t
                LEFT JOIN task_requirements tr ON t.id = tr.task_id
                LEFT JOIN projects p on t.project_id = p.id
                WHERE t.project_id = ? AND (p.stage = 'COMPLETED' OR p.stage = t.stage) AND (? = TRUE OR tr.completed = FALSE OR tr.collected < tr.required_amount OR tr.id IS NULL)
                ORDER BY priority_order, t.updated_at DESC, t.name
            """),
            parameterSetter = { statement, queryInput ->
                statement.setInt(1, queryInput.projectId)
                statement.setBoolean(2, queryInput.includeCompleted)
            },
            errorMapper = { GetTasksByProjectIdFailures.DatabaseError },
            resultMapper = { resultSet ->
                val taskOrder = mutableListOf<Int>()
                val taskMap = mutableMapOf<Int, MutableList<TaskRequirement>>()
                val taskDetails = mutableMapOf<Int, TaskData>()

                while (resultSet.next()) {
                    val taskId = resultSet.getInt("id")

                    // Store task details if not already stored
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

                    // Add requirement if it exists
                    val reqId = resultSet.getObject("req_id")
                    if (reqId != null) {
                        val requirement = when (resultSet.getString("req_type")) {
                            "ITEM" -> ItemRequirement(
                                id = resultSet.getInt("req_id"),
                                item = resultSet.getString("item"),
                                requiredAmount = resultSet.getInt("required_amount"),
                                collected = resultSet.getInt("collected")
                            )
                            "ACTION" -> ActionRequirement(
                                id = resultSet.getInt("req_id"),
                                action = resultSet.getString("action"),
                                completed = resultSet.getBoolean("completed")
                            )
                            else -> throw IllegalStateException("Unknown requirement type: ${resultSet.getString("req_type")}")
                        }
                        taskMap[taskId]?.add(requirement)
                    }
                }

                // Convert to Task objects
                taskOrder.mapNotNull { taskId ->
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
        )

        return tasksStep.process(input)
    }
}

object CountTotalTasksStep : Step<Int, GetTasksByProjectIdFailures, Int> {
    override suspend fun process(input: Int): Result<GetTasksByProjectIdFailures, Int> {
        return DatabaseSteps.query<Int, GetTasksByProjectIdFailures, Int>(
            sql = SafeSQL.select("SELECT COUNT(id) as task_count FROM tasks WHERE project_id = ?"),
            parameterSetter = { statement, projectId ->
                statement.setInt(1, projectId)
            },
            errorMapper = { GetTasksByProjectIdFailures.DatabaseError },
            resultMapper = { resultSet ->
                if (resultSet.next()) {
                    resultSet.getInt("task_count")
                } else {
                    0
                }
            }
        ).process(input)
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
