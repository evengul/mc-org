package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.Task
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure

data class CompleteTaskInput(
    val taskId: Int,
    val userId: Int,
    val projectId: Int
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
            sql = SafeSQL.select("SELECT 1 FROM tasks WHERE (requirement_action_completed = FALSE OR requirement_item_collected < requirement_item_required_amount) AND id = ?"),
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

object CompleteTaskStep : Step<CompleteTaskInput, CompleteTaskFailures, Int> {
    override suspend fun process(input: CompleteTaskInput): Result<CompleteTaskFailures, Int> {
        return DatabaseSteps.transaction(
            object : Step<CompleteTaskInput, CompleteTaskFailures.DatabaseError, Unit> {
                override suspend fun process(input: CompleteTaskInput): Result<CompleteTaskFailures.DatabaseError, Unit> {
                    DatabaseSteps.update<Int, DatabaseFailure>(
                        SafeSQL.update("UPDATE tasks SET requirement_action_completed = TRUE WHERE requirement_type = 'ACTION' and id = ?"),
                        parameterSetter = { statement, taskId -> statement.setInt(1, taskId) },
                        errorMapper = { it }
                    ).process(input.taskId)

                    DatabaseSteps.update<Int, DatabaseFailure>(
                        SafeSQL.update("UPDATE tasks SET requirement_item_collected = requirement_item_required_amount WHERE requirement_type = 'ITEM' AND id = ?"),
                        parameterSetter = { statement, taskId -> statement.setInt(1, taskId) },
                        errorMapper = { it }
                    ).process(input.taskId)

                    return Result.success()
                }
            },
            errorMapper = { CompleteTaskFailures.DatabaseError }
        ).process(input).map { input.taskId }
    }
}

object CheckAnyTasksStepAfterCompletion : Step<Int, CompleteTaskFailures, Pair<Task, Pair<Int, Int>>> {
    override suspend fun process(input: Int): Result<CompleteTaskFailures, Pair<Task, Pair<Int, Int>>> {
        val task = GetTaskStep.process(input)

        val tasksCount = CountTasksInProjectWithTaskIdStep.process(input).getOrNull() ?: 0
        val completedCount = CountCompletedTasksStep.process(input).getOrNull() ?: 0

        if (task is Result.Failure) {
            return Result.failure(CompleteTaskFailures.DatabaseError)
        }

        return Result.success(Pair(task.getOrNull()!!, Pair(tasksCount, completedCount)))
    }
}
