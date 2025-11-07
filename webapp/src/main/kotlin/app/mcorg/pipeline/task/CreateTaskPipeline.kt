package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.Priority
import app.mcorg.domain.model.task.Task
import app.mcorg.domain.model.task.TaskProjectStage
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.task.commonsteps.CountCompletedTasksStep
import app.mcorg.pipeline.task.commonsteps.CountTasksInProjectWithTaskIdStep
import app.mcorg.pipeline.task.commonsteps.GetTaskStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.project.projectProgress
import app.mcorg.presentation.templated.project.taskItem
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.html.div
import kotlinx.html.li
import kotlinx.html.stream.createHTML
import java.sql.Types

data class CreateTaskInput(
    val name: String,
    val priority: Priority,
    val stage: TaskProjectStage,
    val requiredAmount: Int?
)

suspend fun ApplicationCall.handleCreateTask() {
    val parameters = this.receiveParameters()
    val user = this.getUser()
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().li {
                taskItem(worldId, projectId, it.first)
            } + createHTML().div {
                hxOutOfBands("delete:#empty-tasks-state")
            } + createHTML().div {
                hxOutOfBands("innerHTML:#project-progress")
                div {
                    projectProgress(it.second.second, it.second.first)
                }
            })
        },
    ) {
        value(parameters)
            .step(ValidateTaskInputStep)
            .step(CreateTaskStep(projectId, user.id))
            .step(GetUpdatedTaskStep)
    }
}

object ValidateTaskInputStep : Step<Parameters, AppFailure.ValidationError, CreateTaskInput> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, CreateTaskInput> {

        val priority = ValidationSteps.validateCustom<AppFailure.ValidationError, String?>(
            "priority",
            "Invalid task priority",
            errorMapper = { AppFailure.ValidationError(listOf(it)) },
            predicate = {
                it.isNullOrBlank() || runCatching {
                    Priority.valueOf(it.uppercase())
                }.isSuccess
            }).process(input["priority"]).map {
                if (it.isNullOrBlank()) Priority.MEDIUM else Priority.valueOf(it.uppercase())
            }

        val stage = ValidationSteps.validateCustom<AppFailure.ValidationError, String?>(
            "stage",
            "Invalid task stage",
            errorMapper = { AppFailure.ValidationError(listOf(it)) },
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
                else ValidationSteps.validateRange("requiredAmount", 1, 2_000_000_000) { it }.process(requiredAmount)
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
            return Result.failure(AppFailure.ValidationError(errors.toList()))
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
                name = name,
                priority = priority.getOrNull()!!,
                stage = stage.getOrNull()!!,
                requiredAmount = requirement.getOrNull()
            )
        )
    }
}

data class CreateTaskStep(val projectId: Int, val userId: Int) : Step<CreateTaskInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: CreateTaskInput): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<CreateTaskInput>(
            sql = SafeSQL.insert("""
                            INSERT INTO tasks (project_id, name, description, stage, priority, requirement_type, requirement_item_required_amount, requirement_item_collected, requirement_action_completed, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                            RETURNING id
                        """),
            parameterSetter = { statement, _ ->
                statement.setInt(1, projectId)
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
            }
        ).process(input)
    }

}

object GetUpdatedTaskStep : Step<Int, AppFailure.DatabaseError, Pair<Task, Pair<Int, Int>>> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Pair<Task, Pair<Int, Int>>> {
        val task = GetTaskStep.process(input)

        val taskCount = CountTasksInProjectWithTaskIdStep.process(input).getOrNull() ?: 0
        val completedCount = CountCompletedTasksStep.process(input).getOrNull() ?: 0

        if (task is Result.Failure) {
            return task
        }

        return Result.success(Pair(task.getOrNull()!!, Pair(taskCount, completedCount)))
    }
}

