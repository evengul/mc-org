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
import app.mcorg.pipeline.project.GetTasksByProjectIdInput
import app.mcorg.pipeline.project.GetTasksByProjectIdStep
import io.ktor.http.Parameters
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface TaskRequirementInput {
    val name: String

    @Serializable
    data class ItemRequirementInput(
        override val name: String,
        val requiredAmount: Int
    ) : TaskRequirementInput

    data class ActionRequirementInput(
        override val name: String
    ) : TaskRequirementInput
}

data class CreateTaskInput(
    val projectId: Int,
    val creatorId: Int,
    val name: String,
    val description: String?,
    val priority: Priority,
    val stage: TaskProjectStage,
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
        if (stage is Result.Failure) {
            errors.addAll(stage.error.errors)
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
                stage = stage.getOrNull()!!,
                requirements = requirements
            )
        )
    }

    fun parseRequirementsFromParameters(parameters: Parameters): List<TaskRequirementInput> {
        val requirements = mutableListOf<TaskRequirementInput>()
        val parameterNames = parameters.names()

        fun findIndices(name: String): List<Int> {
            return parameterNames
                .filter { it.startsWith("$name[") && it.endsWith("]") }
                .mapNotNull { paramName ->
                    val indexMatch = Regex("${Regex.escape("$name[")}(\\d+)${Regex.escape("]")}").find(paramName)
                    indexMatch?.groupValues?.get(1)?.toIntOrNull()
                }
                .distinct()
                .sorted()
        }

        val itemRequirementIndices = findIndices("itemRequirements")

        val actionRequirementIndices = findIndices("actionRequirements")

        for (index in itemRequirementIndices) {
            val json = parameters["itemRequirements[$index]"] ?: continue
            requirements.add(Json.decodeFromString<TaskRequirementInput.ItemRequirementInput>(json))
        }

        for (index in actionRequirementIndices) {
            val text = parameters["actionRequirements[$index]"] ?: continue
            requirements.add(TaskRequirementInput.ActionRequirementInput(text))
        }

        return requirements
    }

    fun validateRequirements(requirements: List<TaskRequirementInput>): List<ValidationFailure> {
        val errors = mutableListOf<ValidationFailure>()

        requirements.forEachIndexed { index, requirement ->
            when (requirement) {
                is TaskRequirementInput.ItemRequirementInput -> {
                    if (requirement.name.isBlank()) {
                        errors.add(ValidationFailure.MissingParameter("requirements[$index].item"))
                    } else if (requirement.name.length > 100) {
                        errors.add(ValidationFailure.InvalidLength("requirements[$index].item", 1, 100))
                    }

                    if (requirement.requiredAmount <= 0) {
                        errors.add(ValidationFailure.InvalidFormat("requirements[$index].requiredAmount", "Must be a positive number"))
                    } else if (requirement.requiredAmount > 999999) {
                        errors.add(ValidationFailure.InvalidFormat("requirements[$index].requiredAmount", "Amount cannot exceed 999,999"))
                    }
                }
                is TaskRequirementInput.ActionRequirementInput -> {
                    if (requirement.name.isBlank()) {
                        errors.add(ValidationFailure.MissingParameter("requirements[$index].action"))
                    } else if (requirement.name.length > 500) {
                        errors.add(ValidationFailure.InvalidLength("requirements[$index].action", 1, 500))
                    }
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

object CreateTaskStep : Step<CreateTaskInput, CreateTaskFailures, Int> {
    override suspend fun process(input: CreateTaskInput): Result<CreateTaskFailures, Int> {
        return DatabaseSteps.transaction(
            step = object : Step<CreateTaskInput, CreateTaskFailures, Int> {
                override suspend fun process(transactionInput: CreateTaskInput): Result<CreateTaskFailures, Int> {
                    // First, create the task
                    val taskIdResult = DatabaseSteps.update<CreateTaskInput, CreateTaskFailures>(
                        sql = SafeSQL.insert("""
                            INSERT INTO tasks (project_id, name, description, stage, priority, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, NOW(), NOW())
                            RETURNING id
                        """),
                        parameterSetter = { statement, _ ->
                            statement.setInt(1, transactionInput.projectId)
                            statement.setString(2, transactionInput.name)
                            statement.setString(3, transactionInput.description ?: "")
                            statement.setString(4, transactionInput.stage.name)
                            statement.setString(5, transactionInput.priority.name)
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
                        for (requirement in transactionInput.requirements) {
                            val requirementResult = when (requirement) {
                                is TaskRequirementInput.ItemRequirementInput -> createItemRequirement(taskId, requirement)
                                is TaskRequirementInput.ActionRequirementInput -> createActionRequirement(taskId, requirement)
                            }

                            // If any requirement creation fails, the transaction will be rolled back
                            if (requirementResult is Result.Failure) {
                                return requirementResult
                            }
                        }
                    } else {
                        val requirementResult = createActionRequirement(taskId, TaskRequirementInput.ActionRequirementInput(
                            name = transactionInput.name
                        ))

                        if (requirementResult is Result.Failure) {
                            return requirementResult
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
        requirement: TaskRequirementInput.ItemRequirementInput
    ): Result<CreateTaskFailures, Int> {
        return DatabaseSteps.update<TaskRequirementInput, CreateTaskFailures>(
            sql = SafeSQL.insert("""
                INSERT INTO task_requirements (task_id, type, item, required_amount, collected, completed, created_at, updated_at)
                VALUES (?, 'ITEM', ?, ?, 0, false, NOW(), NOW())
                RETURNING id
            """),
            parameterSetter = { statement, _ ->
                statement.setInt(1, taskId)
                statement.setString(2, requirement.name)
                statement.setInt(3, requirement.requiredAmount)
            },
            errorMapper = { CreateTaskFailures.DatabaseError }
        ).process(requirement)
    }

    private suspend fun createActionRequirement(
        taskId: Int,
        requirement: TaskRequirementInput.ActionRequirementInput
    ): Result<CreateTaskFailures, Int> {
        return DatabaseSteps.update<TaskRequirementInput, CreateTaskFailures>(
            sql = SafeSQL.insert("""
                INSERT INTO task_requirements (task_id, type, action, completed, created_at, updated_at)
                VALUES (?, 'ACTION', ?, false, NOW(), NOW())
                RETURNING id
            """),
            parameterSetter = { statement, _ ->
                statement.setInt(1, taskId)
                statement.setString(2, requirement.name)
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
            GetTasksByProjectIdStep.process(GetTasksByProjectIdInput(projectId = task.projectId))
                .mapError { CreateTaskFailures.DatabaseError }
                .map { tasks -> CreateTaskResult(task = task, updatedTasks = tasks) }
        }
    }
}
