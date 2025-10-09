package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.Task
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.project.GetTasksByProjectIdInput
import app.mcorg.pipeline.project.GetTasksByProjectIdStep

data class CompleteTaskInput(
    val taskId: Int,
    val userId: Int,
    val projectId: Int
)

data class CompleteTaskResult(
    val task: Task,
    val updatedTasks: List<Task>
)

sealed class CompleteTaskFailures {
    object TaskNotFound : CompleteTaskFailures()
    object TaskAlreadyCompleted : CompleteTaskFailures()
    object InsufficientPermissions : CompleteTaskFailures()
    object DatabaseError : CompleteTaskFailures()
}

object ValidateTaskCompletionStep : Step<CompleteTaskInput, CompleteTaskFailures, CompleteTaskInput> {
    override suspend fun process(input: CompleteTaskInput): Result<CompleteTaskFailures, CompleteTaskInput> {
        return DatabaseSteps.query<CompleteTaskInput, CompleteTaskFailures, Boolean>(
            sql = SafeSQL.select("SELECT 1 FROM task_requirements WHERE (completed = FALSE OR collected < required_amount) AND task_id = ?"),
            parameterSetter = { statement, _ ->
                statement.setInt(1, input.taskId)
            },
            errorMapper = { CompleteTaskFailures.DatabaseError },
            resultMapper = { it.next() }
        ).process(input).flatMap { anyIncomplete ->
            when (anyIncomplete) {
                false -> Result.failure(CompleteTaskFailures.TaskAlreadyCompleted)
                true -> Result.success(input)
            }
        }
    }
}

object CompleteTaskStep : Step<CompleteTaskInput, CompleteTaskFailures, CompleteTaskInput> {
    override suspend fun process(input: CompleteTaskInput): Result<CompleteTaskFailures, CompleteTaskInput> {
        return DatabaseSteps.transaction(
            object : Step<CompleteTaskInput, CompleteTaskFailures.DatabaseError, Unit> {
                override suspend fun process(input: CompleteTaskInput): Result<CompleteTaskFailures.DatabaseError, Unit> {
                    DatabaseSteps.update<Int, DatabaseFailure>(
                        SafeSQL.update("UPDATE task_requirements SET completed = TRUE WHERE type = 'ACTION' and task_id = ?"),
                        parameterSetter = { statement, taskId -> statement.setInt(1, taskId) },
                        errorMapper = { it }
                    ).process(input.taskId)

                    DatabaseSteps.update<Int, DatabaseFailure>(
                        SafeSQL.update("UPDATE task_requirements SET collected = required_amount WHERE type = 'ITEM' AND task_id = ?"),
                        parameterSetter = { statement, taskId -> statement.setInt(1, taskId) },
                        errorMapper = { it }
                    ).process(input.taskId)

                    return Result.success()
                }
            },
            errorMapper = { CompleteTaskFailures.DatabaseError }
        ).process(input).map { input }
    }
}

object GetUpdatedTasksAfterCompletionStep : Step<CompleteTaskInput, CompleteTaskFailures, CompleteTaskResult> {
    override suspend fun process(input: CompleteTaskInput): Result<CompleteTaskFailures, CompleteTaskResult> {
        // Get the completed task
        val taskResult = DatabaseSteps.query<CompleteTaskInput, CompleteTaskFailures, Task>(
            sql = SafeSQL.select("""
                SELECT t.id, t.project_id, t.name, t.description, t.stage, t.priority
                FROM tasks t
                WHERE t.id = ?
            """),
            parameterSetter = { statement, _ ->
                statement.setInt(1, input.taskId)
            },
            errorMapper = { CompleteTaskFailures.DatabaseError },
            resultMapper = { rs ->
                rs.next()
                rs.toTask()
            }
        ).process(input)

        return taskResult.flatMap { task ->
            GetTasksByProjectIdStep.process(GetTasksByProjectIdInput(input.projectId))
                .mapError { CompleteTaskFailures.DatabaseError }
                .map { tasks ->
                    CompleteTaskResult(
                        task = task,
                        updatedTasks = tasks
                    )
            }
        }
    }
}
