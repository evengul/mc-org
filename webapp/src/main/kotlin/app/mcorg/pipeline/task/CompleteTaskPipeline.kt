package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.Task
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

data class CompleteTaskInput(
    val taskId: Int,
    val userId: Int,
    val projectId: Int
)

data class CompleteTaskResult(
    val task: Task,
    val projectAdvanced: Boolean,
    val updatedTasks: List<Task>
)

sealed class CompleteTaskFailures {
    object TaskNotFound : CompleteTaskFailures()
    object TaskAlreadyCompleted : CompleteTaskFailures()
    object InsufficientPermissions : CompleteTaskFailures()
    object DatabaseError : CompleteTaskFailures()
}

object ValidateTaskAccessStep : Step<CompleteTaskInput, CompleteTaskFailures, CompleteTaskInput> {
    override suspend fun process(input: CompleteTaskInput): Result<CompleteTaskFailures, CompleteTaskInput> {
        return DatabaseSteps.query<CompleteTaskInput, CompleteTaskFailures, Boolean>(
            sql = SafeSQL.select("""
                SELECT EXISTS(
                    SELECT 1 FROM tasks t
                    JOIN projects p ON t.project_id = p.id
                    JOIN world_members wm ON p.world_id = wm.world_id
                    WHERE t.id = ? AND wm.user_id = ? AND wm.world_role <= ?
                )
            """),
            parameterSetter = { statement, _ ->
                statement.setInt(1, input.taskId)
                statement.setInt(2, input.userId)
                statement.setInt(3, Role.MEMBER.level)
            },
            errorMapper = { CompleteTaskFailures.DatabaseError },
            resultMapper = { rs ->
                rs.next()
                rs.getBoolean(1)
            }
        ).process(input).flatMap { hasAccess ->
            if (hasAccess) {
                Result.success(input)
            } else {
                Result.failure(CompleteTaskFailures.InsufficientPermissions)
            }
        }
    }
}

object ValidateTaskCompletionStep : Step<CompleteTaskInput, CompleteTaskFailures, CompleteTaskInput> {
    override suspend fun process(input: CompleteTaskInput): Result<CompleteTaskFailures, CompleteTaskInput> {
        return DatabaseSteps.query<CompleteTaskInput, CompleteTaskFailures, String>(
            sql = SafeSQL.select("SELECT stage FROM tasks WHERE id = ?"),
            parameterSetter = { statement, _ ->
                statement.setInt(1, input.taskId)
            },
            errorMapper = { CompleteTaskFailures.DatabaseError },
            resultMapper = { rs ->
                if (rs.next()) {
                    rs.getString(1)
                } else {
                    throw Exception("Task not found")
                }
            }
        ).process(input).flatMap { stage ->
            when (stage) {
                "COMPLETED" -> Result.failure(CompleteTaskFailures.TaskAlreadyCompleted)
                else -> Result.success(input)
            }
        }
    }
}

object CompleteTaskStep : Step<CompleteTaskInput, CompleteTaskFailures, CompleteTaskInput> {
    override suspend fun process(input: CompleteTaskInput): Result<CompleteTaskFailures, CompleteTaskInput> {
        return DatabaseSteps.update<CompleteTaskInput, CompleteTaskFailures>(
            sql = SafeSQL.update("""
                UPDATE tasks
                SET stage = 'COMPLETED', updated_at = NOW()
                WHERE id = ? AND stage != 'COMPLETED'
            """),
            parameterSetter = { statement, _ ->
                statement.setInt(1, input.taskId)
            },
            errorMapper = { CompleteTaskFailures.DatabaseError }
        ).process(input).map { input }
    }
}

object CheckProjectProgressStep : Step<CompleteTaskInput, CompleteTaskFailures, Boolean> {
    override suspend fun process(input: CompleteTaskInput): Result<CompleteTaskFailures, Boolean> {
        // Check if all tasks in current project stage are completed
        return DatabaseSteps.query<CompleteTaskInput, CompleteTaskFailures, Boolean>(
            sql = SafeSQL.select("""
                SELECT NOT EXISTS(
                    SELECT 1 FROM tasks t
                    JOIN projects p ON t.project_id = p.id
                    WHERE t.project_id = ?
                    AND t.stage = p.stage
                    AND t.stage != 'COMPLETED'
                )
            """),
            parameterSetter = { statement, _ ->
                statement.setInt(1, input.projectId)
            },
            errorMapper = { CompleteTaskFailures.DatabaseError },
            resultMapper = { rs ->
                rs.next()
                rs.getBoolean(1)
            }
        ).process(input)
    }
}

object GetUpdatedTasksAfterCompletionStep : Step<Pair<CompleteTaskInput, Boolean>, CompleteTaskFailures, CompleteTaskResult> {
    override suspend fun process(input: Pair<CompleteTaskInput, Boolean>): Result<CompleteTaskFailures, CompleteTaskResult> {
        val (taskInput, projectAdvanced) = input

        // Get the completed task
        val taskResult = DatabaseSteps.query<Pair<CompleteTaskInput, Boolean>, CompleteTaskFailures, Task>(
            sql = SafeSQL.select("""
                SELECT t.id, t.project_id, t.name, t.description, t.stage, t.priority
                FROM tasks t
                WHERE t.id = ?
            """),
            parameterSetter = { statement, _ ->
                statement.setInt(1, taskInput.taskId)
            },
            errorMapper = { CompleteTaskFailures.DatabaseError },
            resultMapper = { rs ->
                rs.next()
                rs.toTask()
            }
        ).process(input)

        return taskResult.flatMap { task ->
            // Get all tasks for the project
            DatabaseSteps.query<Pair<CompleteTaskInput, Boolean>, CompleteTaskFailures, List<Task>>(
                sql = SafeSQL.select("""
                    SELECT t.id, t.project_id, t.name, t.description, t.stage, t.priority
                    FROM tasks t
                    WHERE t.project_id = ?
                    ORDER BY t.stage DESC, t.created_at DESC
                """),
                parameterSetter = { statement, _ ->
                    statement.setInt(1, task.projectId)
                },
                errorMapper = { CompleteTaskFailures.DatabaseError },
                resultMapper = { rs ->
                    val tasks = mutableListOf<Task>()
                    while (rs.next()) {
                        tasks.add(rs.toTask())
                    }
                    tasks
                }
            ).process(input).map { tasks ->
                CompleteTaskResult(
                    task = task,
                    projectAdvanced = projectAdvanced,
                    updatedTasks = tasks
                )
            }
        }
    }
}
