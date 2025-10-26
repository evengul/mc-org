package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.Parameters

data class UpdateRequirementProgressInput(
    val taskId: Int,
    val projectId: Int,
    val userId: Int,
    val amount: Int? = null // For item requirements, null for action requirements
)

sealed class UpdateRequirementFailures {
    data class ValidationError(val errors: List<ValidationFailure>) : UpdateRequirementFailures()
    object RequirementNotFound : UpdateRequirementFailures()
    object InsufficientPermissions : UpdateRequirementFailures()
    object InvalidAmount : UpdateRequirementFailures()
    object DatabaseError : UpdateRequirementFailures()
}

object ValidateRequirementProgressInputStep : Step<Parameters, UpdateRequirementFailures, UpdateRequirementProgressInput> {
    override suspend fun process(input: Parameters): Result<UpdateRequirementFailures, UpdateRequirementProgressInput> {
        val amount = input["amount"]?.toIntOrNull()

        // Validate amount if provided (for item requirements)
        if (amount != null && amount <= 0) {
            return Result.failure(UpdateRequirementFailures.InvalidAmount)
        }

        if (amount != null && amount > 999999) {
            return Result.failure(UpdateRequirementFailures.InvalidAmount)
        }

        return Result.success(
            UpdateRequirementProgressInput(
                taskId = 0, // Will be injected by InjectRequirementContextStep
                projectId = 0, // Will be injected by InjectRequirementContextStep
                userId = 0, // Will be injected by InjectRequirementContextStep
                amount = amount
            )
        )
    }
}

class InjectRequirementContextStep(
    private val taskId: Int,
    private val projectId: Int,
    private val userId: Int
) : Step<UpdateRequirementProgressInput, UpdateRequirementFailures, UpdateRequirementProgressInput> {
    override suspend fun process(input: UpdateRequirementProgressInput): Result<UpdateRequirementFailures, UpdateRequirementProgressInput> {
        return Result.success(
            input.copy(
                taskId = taskId,
                projectId = projectId,
                userId = userId
            )
        )
    }
}

object UpdateItemRequirementProgressStep : Step<UpdateRequirementProgressInput, UpdateRequirementFailures, Unit> {
    override suspend fun process(input: UpdateRequirementProgressInput): Result<UpdateRequirementFailures, Unit> {
        return DatabaseSteps.update<UpdateRequirementProgressInput, UpdateRequirementFailures>(
            sql = SafeSQL.update(
                """
                        UPDATE tasks
                            SET requirement_action_completed = CASE WHEN requirement_type = 'ACTION' THEN NOT requirement_action_completed END,
                            requirement_item_collected = CASE WHEN requirement_type = 'ITEM' THEN LEAST(requirement_item_required_amount, requirement_item_collected + ?) END,
                            updated_at = NOW()
                        WHERE id = ?
                    """
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, input.amount ?: 0)
                statement.setInt(2, input.taskId)
            },
            errorMapper = { UpdateRequirementFailures.DatabaseError }
        ).process(input).map {  }
    }
}

object CheckTaskCompletionStep : Step<Int, UpdateRequirementFailures, Pair<Int, Int>> {
    override suspend fun process(input: Int): Result<UpdateRequirementFailures, Pair<Int, Int>> {
        val totalTasks = CountTasksInProjectWithTaskIdStep.process(input).getOrNull() ?: 0
        val completedTasks = CountCompletedTasksStep.process(input).getOrNull() ?: 0

        return Result.success(Pair(totalTasks, completedTasks))
    }
}
