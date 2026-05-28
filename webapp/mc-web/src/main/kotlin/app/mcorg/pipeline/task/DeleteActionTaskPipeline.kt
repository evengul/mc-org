package app.mcorg.pipeline.task

import app.mcorg.config.CacheManager
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.task.commonsteps.CountActionTasksInProjectStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.pages.taskProgressOobFragment
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getTaskId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

suspend fun ApplicationCall.handleDeleteActionTask() {
    val projectId = this.getProjectId()
    val taskId = this.getTaskId()

    handlePipeline(
        onSuccess = { (total, completed) ->
            respondHtml(taskProgressOobFragment(completed, total))
        },
    ) {
        DeleteTaskStep.run(taskId)
        CacheManager.onTaskDeleted(projectId, taskId)
        GetTaskCountsAfterDeletionStep.run(projectId)
    }
}

private object DeleteTaskStep : Step<Int, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Unit> {
        return DatabaseSteps.update<Int>(
            sql = SafeSQL.delete("DELETE FROM action_task WHERE id = ?"),
            parameterSetter = { statement, taskId -> statement.setInt(1, taskId) }
        ).process(input).map { }
    }
}

private object GetTaskCountsAfterDeletionStep : Step<Int, AppFailure.DatabaseError, Pair<Int, Int>> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Pair<Int, Int>> {
        val total = CountActionTasksInProjectStep.process(input).getOrNull() ?: 0
        val completed = DatabaseSteps.query<Int, Int>(
            sql = SafeSQL.select("SELECT COUNT(id) FROM action_task WHERE project_id = ? AND completed = TRUE"),
            parameterSetter = { stmt, projectId -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> if (rs.next()) rs.getInt(1) else 0 }
        ).process(input).getOrNull() ?: 0

        return Result.success(Pair(total, completed))
    }
}
