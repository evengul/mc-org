package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.ActionTask
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.task.commonsteps.CountActionTasksInProjectWithTaskIdStep
import app.mcorg.pipeline.task.commonsteps.CountCompletedActionTasksStep
import app.mcorg.pipeline.task.commonsteps.GetActionTaskStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.project.projectProgress
import app.mcorg.presentation.templated.project.taskItem
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.div
import kotlinx.html.li
import kotlinx.html.stream.createHTML

private data class UpdatedActionTaskProgress(
    val task: ActionTask,
    val totalTasksInProject: Int,
    val completedTasksInProject: Int
)

suspend fun ApplicationCall.handleCreateActionTask() {
    val parameters = this.receiveParameters()
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().li {
                taskItem(worldId, projectId, it.task)
            } + createHTML().div {
                hxOutOfBands("delete:#empty-tasks-state")
            } + createHTML().div {
                hxOutOfBands("innerHTML:#project-progress")
                div {
                    projectProgress(it.completedTasksInProject, it.totalTasksInProject)
                }
            })
        },
    ) {
        value(parameters)
            .step(ValidateCreateActionTaskInputStep)
            .step(CreateActionTaskStep(projectId))
            .step(GetUpdatedActionTaskCountsStep)
    }
}

private object ValidateCreateActionTaskInputStep : Step<Parameters, AppFailure.ValidationError, String> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, String> {
        return ValidationSteps.required("name") { it }.process(input)
            .flatMap { name ->
                ValidationSteps.validateLength("name", minLength = 3, maxLength = 100) { it }.process(name)
            }.mapError { AppFailure.ValidationError(listOf(it)) }
    }
}

private data class CreateActionTaskStep(val projectId: Int) : Step<String, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: String): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<String>(
            sql = SafeSQL.insert(
                """
                INSERT INTO action_task (project_id, name, completed)
                VALUES (?, ?, FALSE)
                RETURNING id
            """.trimIndent()
            ),
            parameterSetter = { statement, name ->
                statement.setInt(1, projectId)
                statement.setString(2, name)
            }
        ).process(input)
    }
}

private object GetUpdatedActionTaskCountsStep : Step<Int, AppFailure.DatabaseError, UpdatedActionTaskProgress> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, UpdatedActionTaskProgress> {
        val task = GetActionTaskStep.process(input)

        if (task is Result.Failure) {
            return task
        }

        val taskCount = CountActionTasksInProjectWithTaskIdStep.process(input).getOrNull() ?: 0
        val completedCount = CountCompletedActionTasksStep.process(input).getOrNull() ?: 0

        return Result.success(UpdatedActionTaskProgress(task.getOrNull()!!, taskCount, completedCount))
    }
}