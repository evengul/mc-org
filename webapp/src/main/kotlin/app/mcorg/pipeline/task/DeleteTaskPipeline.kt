package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.Task
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

data class DeleteTaskInput(
    val taskId: Int,
    val userId: Int,
    val projectId: Int
)

data class DeleteTaskResult(
    val updatedTasks: List<Task>
)

sealed class DeleteTaskFailures {
    object TaskNotFound : DeleteTaskFailures()
    object InsufficientPermissions : DeleteTaskFailures()
    object TaskHasDependencies : DeleteTaskFailures()
    object DatabaseError : DeleteTaskFailures()
}

object ValidateTaskOwnershipStep : Step<DeleteTaskInput, DeleteTaskFailures, DeleteTaskInput> {
    override suspend fun process(input: DeleteTaskInput): Result<DeleteTaskFailures, DeleteTaskInput> {
        return DatabaseSteps.query<DeleteTaskInput, DeleteTaskFailures, Boolean>(
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
            errorMapper = { DeleteTaskFailures.DatabaseError },
            resultMapper = { rs ->
                rs.next()
                rs.getBoolean(1)
            }
        ).process(input).flatMap { hasAccess ->
            if (hasAccess) {
                Result.success(input)
            } else {
                Result.failure(DeleteTaskFailures.InsufficientPermissions)
            }
        }
    }
}

object ValidateTaskDependenciesStep : Step<DeleteTaskInput, DeleteTaskFailures, DeleteTaskInput> {
    override suspend fun process(input: DeleteTaskInput): Result<DeleteTaskFailures, DeleteTaskInput> {
        // For now, we'll implement a simple check - in the future this could check task_dependencies table
        // Currently just ensuring the task exists
        return DatabaseSteps.query<DeleteTaskInput, DeleteTaskFailures, Boolean>(
            sql = SafeSQL.select("SELECT EXISTS(SELECT 1 FROM tasks WHERE id = ?)"),
            parameterSetter = { statement, _ ->
                statement.setInt(1, input.taskId)
            },
            errorMapper = { DeleteTaskFailures.DatabaseError },
            resultMapper = { rs ->
                rs.next()
                rs.getBoolean(1)
            }
        ).process(input).flatMap { exists ->
            if (exists) {
                Result.success(input)
            } else {
                Result.failure(DeleteTaskFailures.TaskNotFound)
            }
        }
    }
}

object DeleteTaskStep : Step<DeleteTaskInput, DeleteTaskFailures, DeleteTaskInput> {
    override suspend fun process(input: DeleteTaskInput): Result<DeleteTaskFailures, DeleteTaskInput> {
        return DatabaseSteps.update<DeleteTaskInput, DeleteTaskFailures>(
            sql = SafeSQL.delete("DELETE FROM tasks WHERE id = ?"),
            parameterSetter = { statement, _ ->
                statement.setInt(1, input.taskId)
            },
            errorMapper = { DeleteTaskFailures.DatabaseError }
        ).process(input).map { input }
    }
}

object GetUpdatedTasksAfterDeletionStep : Step<DeleteTaskInput, DeleteTaskFailures, DeleteTaskResult> {
    override suspend fun process(input: DeleteTaskInput): Result<DeleteTaskFailures, DeleteTaskResult> {
        return DatabaseSteps.query<DeleteTaskInput, DeleteTaskFailures, List<Task>>(
            sql = SafeSQL.select("""
                SELECT t.id, t.project_id, t.name, t.description, t.stage, t.priority
                FROM tasks t
                WHERE t.project_id = ?
                ORDER BY t.created_at DESC
            """),
            parameterSetter = { statement, _ ->
                statement.setInt(1, input.projectId)
            },
            errorMapper = { DeleteTaskFailures.DatabaseError },
            resultMapper = { rs ->
                val tasks = mutableListOf<Task>()
                while (rs.next()) {
                    tasks.add(rs.toTask())
                }
                tasks
            }
        ).process(input).map { tasks ->
            DeleteTaskResult(updatedTasks = tasks)
        }
    }
}
