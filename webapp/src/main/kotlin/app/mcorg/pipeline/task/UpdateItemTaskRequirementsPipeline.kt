package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.ItemRequirement
import app.mcorg.domain.model.task.Task
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.task.commonsteps.CountCompletedTasksStep
import app.mcorg.pipeline.task.commonsteps.CountTasksInProjectWithTaskIdStep
import app.mcorg.pipeline.task.commonsteps.GetTaskStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.project.projectProgress
import app.mcorg.presentation.templated.project.taskCompletionCheckbox
import app.mcorg.presentation.templated.project.taskItemProgress
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getTaskId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.li
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleUpdateRequirementProgress() {
    val parameters = this.receiveParameters()
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val taskId = this.getTaskId()

    executePipeline(
        onSuccess = {
            var baseHtml = createHTML().div {
                attributes["hx-swap-oob"] = "innerHTML:#project-progress"
                div {
                    projectProgress(it.third, it.second)
                }
            }
            if (it.first.isCompleted()) {
                baseHtml += createHTML().input {
                    hxOutOfBands("true")
                    taskCompletionCheckbox(
                        worldId = worldId,
                        projectId = projectId,
                        taskId = taskId,
                        completed = true
                    )
                }
            }
            if (it.first.requirement is ItemRequirement) {
                respondHtml(baseHtml + createHTML().li {
                    taskItemProgress(taskId, it.first.requirement as ItemRequirement)
                })
            } else {
                respondHtml(baseHtml)
            }
        },
        onFailure = { respond(HttpStatusCode.InternalServerError) }
    ) {
        step(Step.value(parameters))
            .step(ValidateUpdateItemTaskRequirementsInputStep)
            .step(UpdateItemTaskRequirement(taskId))
            .value(taskId)
            .step(GetUpdatedTaskCountsStep)
    }
}

private object ValidateUpdateItemTaskRequirementsInputStep : Step<Parameters, AppFailure.ValidationError, Int> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, Int> {
        val amount = ValidationSteps.requiredInt("amount") { it }
            .process(input)
            .flatMap { providedAmount ->
                ValidationSteps.validateRange("amount", 1, Int.MAX_VALUE) { it }
                    .process(providedAmount)
            }

        return when (amount) {
            is Result.Success -> Result.success(amount.value)
            is Result.Failure -> Result.failure(AppFailure.ValidationError(listOf(amount.error)))
        }
    }
}

private data class UpdateItemTaskRequirement(val taskId: Int) : Step<Int, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Unit> {
        return DatabaseSteps.update<Int>(
            sql = SafeSQL.update("""
                UPDATE tasks SET requirement_item_collected = LEAST(requirement_item_collected + ?, requirement_item_required_amount) WHERE id = ?
            """.trimIndent()),
            parameterSetter = { statement, amount ->
                statement.setInt(1, amount)
                statement.setInt(2, taskId)
            }
        ).process(input).map {  }
    }
}

private object GetUpdatedTaskCountsStep : Step<Int, AppFailure.DatabaseError, Triple<Task, Int, Int>> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Triple<Task, Int, Int>> {
        val task = GetTaskStep.process(input)

        val taskCount = CountTasksInProjectWithTaskIdStep.process(input).getOrNull() ?: 0
        val completedCount = CountCompletedTasksStep.process(input).getOrNull() ?: 0

        if (task is Result.Failure) {
            return task
        }

        return Result.success(Triple(task.getOrNull()!!, taskCount, completedCount))
    }
}