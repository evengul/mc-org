package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.ActionTask
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.task.commonsteps.CountActionTasksInProjectWithTaskIdStep
import app.mcorg.pipeline.task.commonsteps.CountCompletedActionTasksStep
import app.mcorg.pipeline.task.commonsteps.GetActionTaskStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.taskRowFragment
import app.mcorg.presentation.templated.dsl.pages.taskProgressOobFragment
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getTaskId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

suspend fun ApplicationCall.handleCompleteActionTask() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val taskId = this.getTaskId()

    handlePipeline(
        onSuccess = {
            respondHtml(
                taskRowFragment(worldId, projectId, it.first) +
                taskProgressOobFragment(it.second.second, it.second.first)
            )
        },
    ) {
        ToggleTaskStep.run(taskId)
        CheckTaskProgressStep.run(taskId)
    }
}

private object ToggleTaskStep : Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<Int>(
            sql = SafeSQL.update("""
                UPDATE action_task
                SET updated_at = CURRENT_TIMESTAMP,
                    completed = NOT completed
                WHERE id = ?
            """.trimIndent()),
            parameterSetter = { statement, taskId -> statement.setInt(1, taskId) }
        ).process(input).map { input }
    }
}

private object CheckTaskProgressStep : Step<Int, AppFailure.DatabaseError, Pair<ActionTask, Pair<Int, Int>>> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Pair<ActionTask, Pair<Int, Int>>> {
        val task = GetActionTaskStep.process(input)

        val tasksCount = CountActionTasksInProjectWithTaskIdStep.process(input).getOrNull() ?: 0
        val completedCount = CountCompletedActionTasksStep.process(input).getOrNull() ?: 0

        if (task is Result.Failure) {
            return task
        }

        return Result.success(Pair(task.getOrNull()!!, Pair(tasksCount, completedCount)))
    }
}
