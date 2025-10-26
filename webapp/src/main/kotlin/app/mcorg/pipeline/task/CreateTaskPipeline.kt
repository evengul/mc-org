package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.Priority
import app.mcorg.domain.model.task.Task
import app.mcorg.domain.model.task.TaskProjectStage
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.Parameters
import java.sql.Types

data class CreateTaskInput(
    val projectId: Int,
    val creatorId: Int,
    val name: String,
    val priority: Priority,
    val stage: TaskProjectStage,
    val requiredAmount: Int?
)

sealed class CreateTaskFailures {
    data class ValidationError(val errors: List<ValidationFailure>) : CreateTaskFailures()
    object InsufficientPermissions : CreateTaskFailures()
    object ProjectNotFound : CreateTaskFailures()
    object DatabaseError : CreateTaskFailures()
}

object ValidateTaskInputStep : Step<Parameters, CreateTaskFailures, CreateTaskInput> {
    override suspend fun process(input: Parameters): Result<CreateTaskFailures, CreateTaskInput> {

        val priority = ValidationSteps.validateCustom<CreateTaskFailures.ValidationError, String?>(
            "priority",
            "Invalid task priority",
            errorMapper = { CreateTaskFailures.ValidationError(listOf(it)) },
            predicate = {
                it.isNullOrBlank() || runCatching {
                    Priority.valueOf(it.uppercase())
                }.isSuccess
            }).process(input["priority"]).map {
                if (it.isNullOrBlank()) Priority.MEDIUM else Priority.valueOf(it.uppercase())
            }

        val stage = ValidationSteps.validateCustom<CreateTaskFailures.ValidationError, String?>(
            "stage",
            "Invalid task stage",
            errorMapper = { CreateTaskFailures.ValidationError(listOf(it)) },
            predicate = {
                it.isNullOrBlank() || runCatching {
                    TaskProjectStage.valueOf(it.uppercase().replace(" ", "_"))
                }.isSuccess
            }).process(input["stage"]).map {
                if (it.isNullOrBlank()) TaskProjectStage.PLANNING else TaskProjectStage.valueOf(it.uppercase().replace(" ", "_"))
            }

        // Parse requirements from form parameters
        val itemName = input["itemName"]?.let { itemName ->
                ValidationSteps.validateLength("itemName", 3, 100) { it }.process(itemName)
            }

        val requirement = ValidationSteps.optionalInt("requiredAmount") { it }
            .process(input)
            .flatMap { requiredAmount ->
                if (requiredAmount == null) Result.success(null)
                else ValidationSteps.validateRange("requiredAmount", 1, 2_000_000) { it }.process(requiredAmount)
            }

        val actionName = input["action"]?.let { actionName ->
                ValidationSteps.validateLength("action", 3, 200) { it }.process(actionName)
        }

        val errors = mutableListOf<ValidationFailure>()
        if (priority is Result.Failure) {
            errors.addAll(priority.error.errors)
        }
        if (stage is Result.Failure) {
            errors.addAll(stage.error.errors)
        }
        if (itemName == null && actionName == null && requirement.getOrNull() == null) {
            errors.add(ValidationFailure.MissingParameter("Either itemName with requiredAmount or action must be provided"))
        }
        if (itemName != null && actionName != null) {
            errors.add(ValidationFailure.CustomValidation("itemName", "Only one of itemName or action can be provided"))
            errors.add(ValidationFailure.CustomValidation("actionName", "Only one of itemName or action can be provided"))
        }

        if (itemName != null && itemName is Result.Failure) {
            errors.add(itemName.error)
        }
        if (actionName != null && actionName is Result.Failure) {
            errors.add(actionName.error)
        }
        if (requirement is Result.Failure) {
            errors.add(requirement.error)
        }

        if (errors.isNotEmpty()) {
            return Result.failure(CreateTaskFailures.ValidationError(errors.toList()))
        }

        val name = if (requirement.getOrNull() != null && itemName != null && itemName is Result.Success) {
            itemName.value
        } else if (actionName != null && actionName is Result.Success) {
            actionName.value
        } else {
            throw IllegalStateException("itemName or actionName must be provided. Should already be handled above.")
        }

        return Result.success(
            CreateTaskInput(
                projectId = 0, // Will be injected by InjectTaskContextStep
                creatorId = 0, // Will be injected by InjectTaskContextStep
                name = name,
                priority = priority.getOrNull()!!,
                stage = stage.getOrNull()!!,
                requiredAmount = requirement.getOrNull()
            )
        )
    }
}

class InjectTaskContextStep(
    private val projectId: Int,
    private val creatorId: Int
) : Step<CreateTaskInput, CreateTaskFailures, CreateTaskInput> {
    override suspend fun process(input: CreateTaskInput): Result<CreateTaskFailures, CreateTaskInput> {
        return Result.success(
            input.copy(
                projectId = projectId,
                creatorId = creatorId
            )
        )
    }
}

object CreateTaskStep : Step<CreateTaskInput, CreateTaskFailures, Int> {
    override suspend fun process(input: CreateTaskInput): Result<CreateTaskFailures, Int> {
        return DatabaseSteps.update<CreateTaskInput, CreateTaskFailures>(
            sql = SafeSQL.insert("""
                            INSERT INTO tasks (project_id, name, description, stage, priority, requirement_type, requirement_item_required_amount, requirement_item_collected, requirement_action_completed, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                            RETURNING id
                        """),
            parameterSetter = { statement, _ ->
                statement.setInt(1, input.projectId)
                statement.setString(2, input.name)
                statement.setString(3, "")
                statement.setString(4, input.stage.name)
                statement.setString(5, input.priority.name)
                if (input.requiredAmount != null) {
                    statement.setString(6, "ITEM")
                    statement.setInt(7, input.requiredAmount)
                    statement.setInt(8, 0)
                    statement.setNull(9, Types.BOOLEAN)
                } else {
                    statement.setString(6, "ACTION")
                    statement.setNull(7, Types.INTEGER)
                    statement.setNull(8, Types.INTEGER)
                    statement.setBoolean(9, false)
                }
            },
            errorMapper = { CreateTaskFailures.DatabaseError }
        ).process(input)
    }

}

object GetUpdatedTaskStep : Step<Int, CreateTaskFailures, Pair<Task, Pair<Int, Int>>> {
    override suspend fun process(input: Int): Result<CreateTaskFailures, Pair<Task, Pair<Int, Int>>> {
        val task = GetTaskStep.process(input)
            .mapError { CreateTaskFailures.DatabaseError }

        val taskCount = CountTasksInProjectWithTaskIdStep.process(input).getOrNull() ?: 0
        val completedCount = CountCompletedTasksStep.process(input).getOrNull() ?: 0

        if (task is Result.Failure) {
            return Result.failure(task.error)
        }

        return Result.success(Pair(task.getOrNull()!!, Pair(taskCount, completedCount)))
    }
}

object CountCompletedTasksStep : Step<Int, CreateTaskFailures, Int> {
    override suspend fun process(input: Int): Result<CreateTaskFailures, Int> {
        return DatabaseSteps.query<Int, CreateTaskFailures, Int>(
            sql = SafeSQL.select("""
                SELECT COUNT(id) FROM tasks WHERE project_id = (
                    SELECT project_id FROM tasks WHERE id = ?
                ) AND (
                    (requirement_type = 'ITEM' AND requirement_item_collected >= requirement_item_required_amount)
                    OR
                    (requirement_type = 'ACTION' AND requirement_action_completed = TRUE)
                )
            """.trimIndent()),
            parameterSetter = { statement, _ -> statement.setInt(1, input) },
            errorMapper = { CreateTaskFailures.DatabaseError },
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

object CountTasksInProjectWithTaskIdStep : Step<Int, CreateTaskFailures, Int> {
    override suspend fun process(input: Int): Result<CreateTaskFailures, Int> {
        return DatabaseSteps.query<Int, CreateTaskFailures, Int>(
            sql = SafeSQL.select("""
                SELECT COUNT(id) FROM tasks WHERE project_id = (
                    SELECT project_id FROM tasks WHERE id = ?
                )
            """.trimIndent()),
            parameterSetter = { statement, _ -> statement.setInt(1, input) },
            errorMapper = { CreateTaskFailures.DatabaseError },
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
