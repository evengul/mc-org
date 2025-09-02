package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.ActionRequirement
import app.mcorg.domain.model.task.ItemRequirement
import app.mcorg.domain.model.task.TaskRequirement
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.Parameters

data class UpdateRequirementProgressInput(
    val requirementId: Int,
    val taskId: Int,
    val projectId: Int,
    val userId: Int,
    val amount: Int? = null // For item requirements, null for action requirements
)

data class UpdateRequirementProgressResult(
    val requirement: TaskRequirement,
    val taskUpdated: Boolean // Whether the parent task completion status changed
)

sealed class UpdateRequirementFailures {
    data class ValidationError(val errors: List<ValidationFailure>) : UpdateRequirementFailures()
    object RequirementNotFound : UpdateRequirementFailures()
    object RequirementAlreadyCompleted : UpdateRequirementFailures()
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
                requirementId = 0, // Will be injected by InjectRequirementContextStep
                taskId = 0, // Will be injected by InjectRequirementContextStep
                projectId = 0, // Will be injected by InjectRequirementContextStep
                userId = 0, // Will be injected by InjectRequirementContextStep
                amount = amount
            )
        )
    }
}

class InjectRequirementContextStep(
    private val requirementId: Int,
    private val taskId: Int,
    private val projectId: Int,
    private val userId: Int
) : Step<UpdateRequirementProgressInput, UpdateRequirementFailures, UpdateRequirementProgressInput> {
    override suspend fun process(input: UpdateRequirementProgressInput): Result<UpdateRequirementFailures, UpdateRequirementProgressInput> {
        return Result.success(
            input.copy(
                requirementId = requirementId,
                taskId = taskId,
                projectId = projectId,
                userId = userId
            )
        )
    }
}

object ValidateRequirementAccessStep : Step<UpdateRequirementProgressInput, UpdateRequirementFailures, UpdateRequirementProgressInput> {
    override suspend fun process(input: UpdateRequirementProgressInput): Result<UpdateRequirementFailures, UpdateRequirementProgressInput> {
        return DatabaseSteps.query<UpdateRequirementProgressInput, UpdateRequirementFailures, Boolean>(
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
            errorMapper = { UpdateRequirementFailures.DatabaseError },
            resultMapper = { rs ->
                rs.next()
                rs.getBoolean(1)
            }
        ).process(input).flatMap { hasAccess ->
            if (hasAccess) {
                Result.success(input)
            } else {
                Result.failure(UpdateRequirementFailures.InsufficientPermissions)
            }
        }
    }
}

object GetRequirementStep : Step<UpdateRequirementProgressInput, UpdateRequirementFailures, Pair<UpdateRequirementProgressInput, TaskRequirement>> {
    override suspend fun process(input: UpdateRequirementProgressInput): Result<UpdateRequirementFailures, Pair<UpdateRequirementProgressInput, TaskRequirement>> {
        return DatabaseSteps.query<UpdateRequirementProgressInput, UpdateRequirementFailures, TaskRequirement?>(
            sql = SafeSQL.select("""
                SELECT id, task_id, type, item, required_amount, collected, action, completed
                FROM task_requirements
                WHERE id = ? AND task_id = ?
            """),
            parameterSetter = { statement, _ ->
                statement.setInt(1, input.requirementId)
                statement.setInt(2, input.taskId)
            },
            errorMapper = { UpdateRequirementFailures.DatabaseError },
            resultMapper = { rs ->
                if (rs.next()) {
                    val type = rs.getString("type")
                    when (type) {
                        "ITEM" -> ItemRequirement(
                            id = rs.getInt("id"),
                            item = rs.getString("item"),
                            requiredAmount = rs.getInt("required_amount"),
                            collected = rs.getInt("collected")
                        )
                        "ACTION" -> ActionRequirement(
                            id = rs.getInt("id"),
                            action = rs.getString("action"),
                            completed = rs.getBoolean("completed")
                        )
                        else -> null
                    }
                } else {
                    null
                }
            }
        ).process(input).flatMap { requirement ->
            if (requirement == null) {
                Result.failure(UpdateRequirementFailures.RequirementNotFound)
            } else if (requirement.isCompleted()) {
                Result.failure(UpdateRequirementFailures.RequirementAlreadyCompleted)
            } else {
                Result.success(Pair(input, requirement))
            }
        }
    }
}

object UpdateItemRequirementProgressStep : Step<Pair<UpdateRequirementProgressInput, TaskRequirement>, UpdateRequirementFailures, TaskRequirement> {
    override suspend fun process(input: Pair<UpdateRequirementProgressInput, TaskRequirement>): Result<UpdateRequirementFailures, TaskRequirement> {
        val (updateInput, requirement) = input

        when (requirement) {
            is ItemRequirement -> {
                if (updateInput.amount == null) {
                    return Result.failure(UpdateRequirementFailures.InvalidAmount)
                }

                val newCollected = (requirement.collected + updateInput.amount).coerceAtMost(requirement.requiredAmount)

                return DatabaseSteps.update<Pair<UpdateRequirementProgressInput, TaskRequirement>, UpdateRequirementFailures>(
                    sql = SafeSQL.update("""
                        UPDATE task_requirements 
                        SET collected = ?, completed = ?, updated_at = NOW()
                        WHERE id = ?
                    """),
                    parameterSetter = { statement, _ ->
                        statement.setInt(1, newCollected)
                        statement.setBoolean(2, newCollected >= requirement.requiredAmount)
                        statement.setInt(3, requirement.id)
                    },
                    errorMapper = { UpdateRequirementFailures.DatabaseError }
                ).process(input).map {
                    requirement.copy(
                        collected = newCollected
                    )
                }
            }
            is ActionRequirement -> {
                // Toggle action requirement
                return DatabaseSteps.update<Pair<UpdateRequirementProgressInput, TaskRequirement>, UpdateRequirementFailures>(
                    sql = SafeSQL.update("""
                        UPDATE task_requirements 
                        SET completed = true, updated_at = NOW()
                        WHERE id = ?
                    """),
                    parameterSetter = { statement, _ ->
                        statement.setInt(1, requirement.id)
                    },
                    errorMapper = { UpdateRequirementFailures.DatabaseError }
                ).process(input).map {
                    requirement.copy(completed = true)
                }
            }
        }
    }
}

object CheckTaskCompletionStep : Step<TaskRequirement, UpdateRequirementFailures, UpdateRequirementProgressResult> {
    override suspend fun process(input: TaskRequirement): Result<UpdateRequirementFailures, UpdateRequirementProgressResult> {
        // Check if all requirements for this task are now completed
        return DatabaseSteps.query<TaskRequirement, UpdateRequirementFailures, Boolean>(
            sql = SafeSQL.select("""
                SELECT NOT EXISTS(
                    SELECT 1 FROM task_requirements
                    WHERE task_id = (SELECT task_id FROM task_requirements WHERE id = ?)
                    AND completed = false
                )
            """),
            parameterSetter = { statement, _ ->
                statement.setInt(1, input.id)
            },
            errorMapper = { UpdateRequirementFailures.DatabaseError },
            resultMapper = { rs ->
                rs.next()
                rs.getBoolean(1)
            }
        ).process(input).flatMap { allCompleted ->
            if (allCompleted) {
                // Update the parent task as completed
                DatabaseSteps.update<TaskRequirement, UpdateRequirementFailures>(
                    sql = SafeSQL.update("""
                        UPDATE tasks 
                        SET updated_at = NOW(), stage = 'COMPLETED'
                        WHERE id = (SELECT task_id FROM task_requirements WHERE id = ?)
                    """),
                    parameterSetter = { statement, _ ->
                        statement.setInt(1, input.id)
                    },
                    errorMapper = { UpdateRequirementFailures.DatabaseError }
                ).process(input).map {
                    UpdateRequirementProgressResult(input, true)
                }
            } else {
                Result.success(UpdateRequirementProgressResult(input, false))
            }
        }
    }
}
