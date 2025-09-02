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

data class TaskRequirementInput(
    val type: String, // "ITEM" or "ACTION"
    val item: String? = null, // For ITEM requirements
    val requiredAmount: Int? = null, // For ITEM requirements
    val action: String? = null // For ACTION requirements
)

data class CreateTaskInput(
    val projectId: Int,
    val creatorId: Int,
    val name: String,
    val description: String?,
    val priority: Priority,
    val requirements: List<TaskRequirementInput> = emptyList()
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

        // Parse requirements from form parameters
        val requirements = parseRequirementsFromParameters(input)

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

        // Validate requirements
        val requirementValidationErrors = validateRequirements(requirements)
        errors.addAll(requirementValidationErrors)

        if (errors.isNotEmpty()) {
            return Result.failure(CreateTaskFailures.ValidationError(errors.toList()))
        }

        return Result.success(
            CreateTaskInput(
                projectId = 0, // Will be injected by InjectTaskContextStep
                creatorId = 0, // Will be injected by InjectTaskContextStep
                name = name.getOrNull()!!,
                description = description.getOrNull(),
                priority = priority.getOrNull()!!,
                requirements = requirements
            )
        )
    }

    fun parseRequirementsFromParameters(parameters: Parameters): List<TaskRequirementInput> {
        val requirements = mutableListOf<TaskRequirementInput>()
        val parameterNames = parameters.names()

        // Find all requirement indices by looking for requirements[X].type parameters
        val requirementIndices = parameterNames
            .filter { it.startsWith("requirements[") && it.endsWith("].type") }
            .mapNotNull { paramName ->
                val indexMatch = Regex("requirements\\[(\\d+)\\]\\.type").find(paramName)
                indexMatch?.groupValues?.get(1)?.toIntOrNull()
            }
            .distinct()
            .sorted()

        // Parse each requirement
        for (index in requirementIndices) {
            val type = parameters["requirements[$index].type"]
            when (type?.uppercase()) {
                "ITEM" -> {
                    val item = parameters["requirements[$index].item"]
                    val amountStr = parameters["requirements[$index].requiredAmount"]
                    val amount = amountStr?.toIntOrNull()

                    if (!item.isNullOrBlank() && amount != null && amount > 0) {
                        requirements.add(
                            TaskRequirementInput(
                                type = "ITEM",
                                item = item.trim(),
                                requiredAmount = amount
                            )
                        )
                    }
                }
                "ACTION" -> {
                    val action = parameters["requirements[$index].action"]

                    if (!action.isNullOrBlank()) {
                        requirements.add(
                            TaskRequirementInput(
                                type = "ACTION",
                                action = action.trim()
                            )
                        )
                    }
                }
            }
        }

        return requirements
    }

    fun validateRequirements(requirements: List<TaskRequirementInput>): List<ValidationFailure> {
        val errors = mutableListOf<ValidationFailure>()

        requirements.forEachIndexed { index, requirement ->
            when (requirement.type.uppercase()) {
                "ITEM" -> {
                    if (requirement.item.isNullOrBlank()) {
                        errors.add(ValidationFailure.MissingParameter("requirements[$index].item"))
                    } else if (requirement.item.length > 100) {
                        errors.add(ValidationFailure.InvalidLength("requirements[$index].item", 1, 100))
                    }

                    if (requirement.requiredAmount == null || requirement.requiredAmount <= 0) {
                        errors.add(ValidationFailure.InvalidFormat("requirements[$index].requiredAmount", "Must be a positive number"))
                    } else if (requirement.requiredAmount > 999999) {
                        errors.add(ValidationFailure.InvalidFormat("requirements[$index].requiredAmount", "Amount cannot exceed 999,999"))
                    }
                }
                "ACTION" -> {
                    if (requirement.action.isNullOrBlank()) {
                        errors.add(ValidationFailure.MissingParameter("requirements[$index].action"))
                    } else if (requirement.action.length > 500) {
                        errors.add(ValidationFailure.InvalidLength("requirements[$index].action", 1, 500))
                    }
                }
                else -> {
                    errors.add(ValidationFailure.InvalidFormat("requirements[$index].type", "Must be ITEM or ACTION"))
                }
            }
        }

        return errors
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
        return DatabaseSteps.transaction(
            step = object : Step<CreateTaskInput, CreateTaskFailures, Int> {
                override suspend fun process(transactionInput: CreateTaskInput): Result<CreateTaskFailures, Int> {
                    // First, create the task
                    val taskIdResult = DatabaseSteps.update<CreateTaskInput, CreateTaskFailures>(
                        sql = SafeSQL.insert("""
                            INSERT INTO tasks (project_id, name, description, stage, priority, created_at, updated_at)
                            VALUES (?, ?, ?, 'PLANNING', ?, NOW(), NOW())
                            RETURNING id
                        """),
                        parameterSetter = { statement, _ ->
                            statement.setInt(1, transactionInput.projectId)
                            statement.setString(2, transactionInput.name)
                            statement.setString(3, transactionInput.description ?: "")
                            statement.setString(4, transactionInput.priority.name)
                        },
                        errorMapper = { CreateTaskFailures.DatabaseError }
                    ).process(transactionInput)

                    // If task creation failed, return the failure
                    if (taskIdResult is Result.Failure) {
                        return taskIdResult
                    }

                    val taskId = taskIdResult.getOrNull()!!

                    // Create requirements if any exist
                    if (transactionInput.requirements.isNotEmpty()) {
                        for ((index, requirement) in transactionInput.requirements.withIndex()) {
                            val requirementResult = when (requirement.type.uppercase()) {
                                "ITEM" -> createItemRequirement(taskId, requirement, index)
                                "ACTION" -> createActionRequirement(taskId, requirement, index)
                                else -> Result.failure(CreateTaskFailures.DatabaseError)
                            }

                            // If any requirement creation fails, the transaction will be rolled back
                            if (requirementResult is Result.Failure) {
                                return requirementResult
                            }
                        }
                    }

                    return Result.success(taskId)
                }
            },
            errorMapper = { CreateTaskFailures.DatabaseError }
        ).process(input)
    }

    private suspend fun createItemRequirement(
        taskId: Int,
        requirement: TaskRequirementInput,
        index: Int
    ): Result<CreateTaskFailures, Int> {
        return DatabaseSteps.update<TaskRequirementInput, CreateTaskFailures>(
            sql = SafeSQL.insert("""
                INSERT INTO task_requirements (task_id, type, item, required_amount, collected, completed, created_at, updated_at)
                VALUES (?, 'ITEM', ?, ?, 0, false, NOW(), NOW())
                RETURNING id
            """),
            parameterSetter = { statement, _ ->
                statement.setInt(1, taskId)
                statement.setString(2, requirement.item!!)
                statement.setInt(3, requirement.requiredAmount!!)
            },
            errorMapper = { CreateTaskFailures.DatabaseError }
        ).process(requirement)
    }

    private suspend fun createActionRequirement(
        taskId: Int,
        requirement: TaskRequirementInput,
        index: Int
    ): Result<CreateTaskFailures, Int> {
        return DatabaseSteps.update<TaskRequirementInput, CreateTaskFailures>(
            sql = SafeSQL.insert("""
                INSERT INTO task_requirements (task_id, type, action, completed, created_at, updated_at)
                VALUES (?, 'ACTION', ?, false, NOW(), NOW())
                RETURNING id
            """),
            parameterSetter = { statement, _ ->
                statement.setInt(1, taskId)
                statement.setString(2, requirement.action!!)
            },
            errorMapper = { CreateTaskFailures.DatabaseError }
        ).process(requirement)
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
