package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.Priority
import app.mcorg.domain.model.task.Task
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.Parameters

data class CreateTaskInput(
    val projectId: Int,
    val creatorId: Int,
    val name: String,
    val description: String?,
    val priority: Priority
)

data class CreateTaskResult(
    val task: Task,
    val updatedTasks: List<Task>
)

sealed class CreateTaskFailures {
    data class ValidationError(val errors: List<ValidationFailure>) : CreateTaskFailures()
    object InsufficientPermissions : CreateTaskFailures()
    object ProjectNotFound : CreateTaskFailures()
    object DatabaseError : CreateTaskFailures()
}

object ValidateTaskInputStep : Step<Parameters, CreateTaskFailures, CreateTaskInput> {
    override suspend fun process(input: Parameters): Result<CreateTaskFailures, CreateTaskInput> {
        val name = ValidationSteps.required("name", { CreateTaskFailures.ValidationError(listOf(it)) })
            .process(input)
        val description = ValidationSteps.optional("description")
            .process(input)
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

        val errors = mutableListOf<ValidationFailure>()
        if (name is Result.Failure) {
            errors.addAll(name.error.errors)
        }
        if (description is Result.Failure) {
            // Description is optional so no errors expected
        }
        if (priority is Result.Failure) {
            errors.addAll(priority.error.errors)
        }

        // Additional validation for name length
        if (name is Result.Success) {
            val nameValue = name.getOrNull()!!
            if (nameValue.length > 200) {
                errors.add(ValidationFailure.InvalidLength("name", 1, 200))
            }
        }

        // Additional validation for description length
        if (description is Result.Success) {
            val descValue = description.getOrNull()
            if (descValue != null && descValue.length > 2000) {
                errors.add(ValidationFailure.InvalidLength("description", null, 2000))
            }
        }

        if (errors.isNotEmpty()) {
            return Result.failure(CreateTaskFailures.ValidationError(errors.toList()))
        }

        return Result.success(
            CreateTaskInput(
                projectId = 0, // Will be injected by InjectTaskContextStep
                creatorId = 0, // Will be injected by InjectTaskContextStep
                name = name.getOrNull()!!,
                description = description.getOrNull(),
                priority = priority.getOrNull()!!
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

object ValidateProjectAccessStep : Step<CreateTaskInput, CreateTaskFailures, CreateTaskInput> {
    override suspend fun process(input: CreateTaskInput): Result<CreateTaskFailures, CreateTaskInput> {
        return DatabaseSteps.query<CreateTaskInput, CreateTaskFailures, Boolean>(
            sql = SafeSQL.select("""
                SELECT EXISTS(
                    SELECT 1 FROM projects p
                    JOIN world_members wm ON p.world_id = wm.world_id
                    WHERE p.id = ? AND wm.user_id = ? AND wm.world_role <= ?
                )
            """),
            parameterSetter = { statement, _ ->
                statement.setInt(1, input.projectId)
                statement.setInt(2, input.creatorId)
                statement.setInt(3, Role.MEMBER.level)
            },
            errorMapper = { CreateTaskFailures.DatabaseError },
            resultMapper = { rs ->
                rs.next()
                rs.getBoolean(1)
            }
        ).process(input).flatMap { hasAccess ->
            if (hasAccess) {
                Result.success(input)
            } else {
                Result.failure(CreateTaskFailures.InsufficientPermissions)
            }
        }
    }
}

object CreateTaskStep : Step<CreateTaskInput, CreateTaskFailures, Int> {
    override suspend fun process(input: CreateTaskInput): Result<CreateTaskFailures, Int> {
        return DatabaseSteps.update<CreateTaskInput, CreateTaskFailures>(
            sql = SafeSQL.insert("""
                INSERT INTO tasks (project_id, name, description, stage, priority, created_at, updated_at)
                VALUES (?, ?, ?, 'PLANNING', ?, NOW(), NOW())
                RETURNING id
            """),
            parameterSetter = { statement, _ ->
                statement.setInt(1, input.projectId)
                statement.setString(2, input.name)
                statement.setString(3, input.description ?: "")
                statement.setString(4, input.priority.name)
            },
            errorMapper = { CreateTaskFailures.DatabaseError }
        ).process(input)
    }
}

object GetUpdatedTasksStep : Step<Int, CreateTaskFailures, CreateTaskResult> {
    override suspend fun process(input: Int): Result<CreateTaskFailures, CreateTaskResult> {
        val taskResult = DatabaseSteps.query<Int, CreateTaskFailures, Task>(
            sql = SafeSQL.select("""
                SELECT t.id, t.project_id, t.name, t.description, t.stage, t.priority
                FROM tasks t
                WHERE t.id = ?
            """),
            parameterSetter = { statement, _ ->
                statement.setInt(1, input)
            },
            errorMapper = { CreateTaskFailures.DatabaseError },
            resultMapper = { rs ->
                rs.next()
                rs.toTask()
            }
        ).process(input)

        return taskResult.flatMap { task ->
            // Get all tasks for the project
            DatabaseSteps.query<Int, CreateTaskFailures, List<Task>>(
                sql = SafeSQL.select("""
                    SELECT t.id, t.project_id, t.name, t.description, t.stage, t.priority
                    FROM tasks t
                    WHERE t.project_id = ?
                    ORDER BY t.created_at DESC
                """),
                parameterSetter = { statement, _ ->
                    statement.setInt(1, task.projectId)
                },
                errorMapper = { CreateTaskFailures.DatabaseError },
                resultMapper = { rs ->
                    val tasks = mutableListOf<Task>()
                    while (rs.next()) {
                        tasks.add(rs.toTask())
                    }
                    tasks
                }
            ).process(input).map { tasks ->
                CreateTaskResult(task = task, updatedTasks = tasks)
            }
        }
    }
}
