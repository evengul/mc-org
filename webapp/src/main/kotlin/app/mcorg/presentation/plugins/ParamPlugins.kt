package app.mcorg.presentation.plugins

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.setProjectId
import app.mcorg.presentation.utils.setTaskId
import app.mcorg.presentation.utils.setWorldId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond

val WorldParamPlugin = createRouteScopedPlugin("WorldParamPlugin") {
    onCall { call ->
        val worldId = call.parameters["worldId"]?.toIntOrNull()
        if (worldId == null) {
            call.respondBadRequest("Invalid or missing world ID")
        } else {
            val checkResult = ensureWorldExists(worldId)
            if (checkResult is Result.Success && checkResult.value) {
                call.setWorldId(worldId)
            } else if ((checkResult is Result.Success && !checkResult.value) || (checkResult is Result.Failure && checkResult.error is DatabaseFailure.NotFound)) {
                call.respond(HttpStatusCode.NotFound, "World with ID $worldId does not exist")
            } else {
                call.respond(HttpStatusCode.InternalServerError, "Database error occurred")
            }
        }
    }
}

val ProjectParamPlugin = createRouteScopedPlugin("ParamPlugin") {
    onCall { call ->
        val worldId = call.getWorldId()
        val projectId = call.parameters["projectId"]?.toIntOrNull()
        if (projectId == null) {
            call.respondBadRequest("Invalid or missing project ID")
        } else {
            val checkResult = ensureProjectExists(worldId, projectId)
            if (checkResult is Result.Success && checkResult.value) {
                call.setProjectId(projectId)
            } else if ((checkResult is Result.Success && !checkResult.value) || (checkResult is Result.Failure && checkResult.error is DatabaseFailure.NotFound)) {
                call.respond(HttpStatusCode.NotFound, "Project with ID $projectId does not exist")
            } else {
                call.respond(HttpStatusCode.InternalServerError, "Database error occurred")
            }
        }
    }
}

val TaskParamPlugin = createRouteScopedPlugin("TaskParamPlugin") {
    onCall { call ->
        val projectId = call.getProjectId()
        val taskId = call.parameters["taskId"]?.toIntOrNull()
        if (taskId == null) {
            call.respondBadRequest("Invalid or missing task ID")
        } else {
            val checkResult = ensureTaskExists(projectId, taskId)
            if (checkResult is Result.Success && checkResult.value) {
                call.setTaskId(taskId)
            } else if ((checkResult is Result.Success && !checkResult.value) || (checkResult is Result.Failure && checkResult.error is DatabaseFailure.NotFound)) {
                call.respond(HttpStatusCode.NotFound, "Task with ID $taskId does not exist")
            } else {
                call.respond(HttpStatusCode.InternalServerError, "Database error occurred")
            }
        }
    }
}

private suspend fun ensureWorldExists(worldId: Int) = ensureParamEntityExists(
    SafeSQL.select("SELECT EXISTS(SELECT 1 FROM world WHERE id = ?)"),
    worldId
)

private suspend fun ensureProjectExists(worldId: Int, projectId: Int) = ensureParamEntityExists(
    SafeSQL.select("SELECT EXISTS(SELECT 1 FROM projects WHERE id = ? AND world_id = ?)"),
    projectId, worldId
)

private suspend fun ensureTaskExists(projectId: Int, taskId: Int) = ensureParamEntityExists(
    SafeSQL.select("SELECT EXISTS(SELECT 1 FROM tasks WHERE id = ? AND project_id = ?)"),
    taskId, projectId
)

private suspend fun ensureParamEntityExists(
    sql: SafeSQL,
    vararg ids: Int
): Result<DatabaseFailure, Boolean> {
    val step = DatabaseSteps.query<List<Int>, DatabaseFailure, Boolean>(
        sql,
        { statement, input -> input.forEachIndexed { index, id -> statement.setInt(index + 1, id)} },
        { it },
        resultMapper = { rs -> rs.next() && rs.getBoolean(1) }
    )
    return step.process(ids.toList())
}