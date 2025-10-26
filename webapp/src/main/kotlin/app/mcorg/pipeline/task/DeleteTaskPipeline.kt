package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

data class DeleteTaskInput(
    val taskId: Int,
    val userId: Int,
    val projectId: Int
)

sealed class DeleteTaskFailures {
    object TaskNotFound : DeleteTaskFailures()
    object InsufficientPermissions : DeleteTaskFailures()
    object TaskHasDependencies : DeleteTaskFailures()
    object DatabaseError : DeleteTaskFailures()
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

object DeleteTaskStep : Step<DeleteTaskInput, DeleteTaskFailures, Int> {
    override suspend fun process(input: DeleteTaskInput): Result<DeleteTaskFailures, Int> {
        return DatabaseSteps.update<DeleteTaskInput, DeleteTaskFailures>(
            sql = SafeSQL.delete("DELETE FROM tasks WHERE id = ?"),
            parameterSetter = { statement, _ ->
                statement.setInt(1, input.taskId)
            },
            errorMapper = { DeleteTaskFailures.DatabaseError }
        ).process(input).map { input.projectId }
    }
}

object GetUpdatedTasksAfterDeletionStep : Step<Int, DeleteTaskFailures, Pair<Int, Int>> {
    override suspend fun process(input: Int): Result<DeleteTaskFailures, Pair<Int, Int>> {
        val taskCount = CountProjectTasksStep.process(input).getOrNull() ?: 0
        val completedCount = CountCompletedTasksInProjectStep.process(input).getOrNull() ?: 0

        return Result.success(Pair(taskCount, completedCount))
    }
}

object CountCompletedTasksInProjectStep : Step<Int, DeleteTaskFailures, Int> {
    override suspend fun process(input: Int): Result<DeleteTaskFailures, Int> {
        return DatabaseSteps.query<Int, DeleteTaskFailures, Int>(
            sql = SafeSQL.select("""
                SELECT COUNT(id) FROM tasks WHERE project_id = ? AND (
                    (requirement_type = 'ITEM' AND requirement_item_collected >= requirement_item_required_amount)
                    OR
                    (requirement_type = 'ACTION' AND requirement_action_completed = TRUE)
                )
            """.trimIndent()),
            parameterSetter = { statement, _ -> statement.setInt(1, input) },
            errorMapper = { DeleteTaskFailures.DatabaseError },
            resultMapper = { rs ->
                if (rs.next()) {
                    rs.getInt(1)
                } else {
                    0
                }
            }
        ).process(input)
    }
}
