package app.mcorg.presentation.plugins

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.setInviteId
import app.mcorg.presentation.utils.setNotificationId
import app.mcorg.presentation.utils.setProjectDependencyId
import app.mcorg.presentation.utils.setProjectId
import app.mcorg.presentation.utils.setProjectProductionItemId
import app.mcorg.presentation.utils.setTaskId
import app.mcorg.presentation.utils.setWorldId
import app.mcorg.presentation.utils.setWorldMemberId
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

val NotificationParamPlugin = createRouteScopedPlugin("NotificationParamPlugin") {
    onCall { call ->
        val notificationId = call.parameters["notificationId"]?.toIntOrNull()
        if (notificationId == null) {
            call.respondBadRequest("Invalid or missing notification ID")
        } else {
            val userId = call.getUser().id
            val checkResult = ensureNotificationExists(userId, notificationId)
            if (checkResult is Result.Success && checkResult.value) {
                call.setNotificationId(notificationId)
            } else if ((checkResult is Result.Success && !checkResult.value) || (checkResult is Result.Failure && checkResult.error is DatabaseFailure.NotFound)) {
                call.respond(HttpStatusCode.NotFound, "Notification with ID $notificationId does not exist")
            } else {
                call.respond(HttpStatusCode.InternalServerError, "Database error occurred")
            }
        }
    }
}

val InviteParamPlugin = createRouteScopedPlugin("InviteParamPlugin") {
    onCall { call ->
        val inviteId = call.parameters["inviteId"]?.toIntOrNull()
        if (inviteId == null) {
            call.respondBadRequest("Invalid or missing invite ID")
        } else {
            val checkResult = ensureInviteExists(inviteId)
            if (checkResult is Result.Success && checkResult.value) {
                call.setInviteId(inviteId)
            } else if ((checkResult is Result.Success && !checkResult.value) || (checkResult is Result.Failure && checkResult.error is DatabaseFailure.NotFound)) {
                call.respond(HttpStatusCode.NotFound, "Invite with ID $inviteId does not exist")
            } else {
                call.respond(HttpStatusCode.InternalServerError, "Database error occurred")
            }
        }
    }
}

val WorldMemberParamPlugin = createRouteScopedPlugin("MemberParamPlugin") {
    onCall { call ->
        val worldId = call.getWorldId()
        val memberId = call.parameters["memberId"]?.toIntOrNull()
        if (memberId == null) {
            call.respondBadRequest("Invalid or missing member ID")
        } else {
            val checkResult = ensureParamEntityExists(
                SafeSQL.select("SELECT EXISTS(SELECT 1 FROM world_members WHERE user_id = ? AND world_id = ?)"),
                memberId, worldId
            )
            if (checkResult is Result.Success && checkResult.value) {
                call.setWorldMemberId(memberId)
            } else if ((checkResult is Result.Success && !checkResult.value) || (checkResult is Result.Failure && checkResult.error is DatabaseFailure.NotFound)) {
                call.respond(HttpStatusCode.NotFound, "Member with ID $memberId does not exist in the world")
            } else {
                call.respond(HttpStatusCode.InternalServerError, "Database error occurred")
            }
        }
    }
}

val ProjectProductionItemParamPlugin = createRouteScopedPlugin("ProjectProductionItemParamPlugin") {
    onCall { call ->
        val projectId = call.getProjectId()
        val itemId = call.parameters["resourceId"]?.toIntOrNull()
        if (itemId == null) {
            call.respondBadRequest("Invalid or missing project production item ID")
        } else {
            val checkResult = ensureParamEntityExists(
                SafeSQL.select("SELECT EXISTS(SELECT 1 FROM project_productions WHERE id = ? AND project_id = ?)"),
                itemId, projectId
            )
            if (checkResult is Result.Success && checkResult.value) {
                call.setProjectProductionItemId(itemId)
            } else if ((checkResult is Result.Success && !checkResult.value) || (checkResult is Result.Failure && checkResult.error is DatabaseFailure.NotFound)) {
                call.respond(HttpStatusCode.NotFound, "Project production item with ID $itemId does not exist")
            } else {
                call.respond(HttpStatusCode.InternalServerError, "Database error occurred")
            }
        }
    }
}

val ProjectDependencyItemPlugin = createRouteScopedPlugin("ProjectDependencyItemPlugin") {
    onCall { call ->
        val projectId = call.getProjectId()
        val dependencyId = call.parameters["dependencyId"]?.toIntOrNull()
        if (dependencyId == null) {
            call.respondBadRequest("Invalid or missing project dependency ID")
        } else {
            val checkResult = ensureParamEntityExists(
                SafeSQL.select("SELECT EXISTS(SELECT 1 FROM project_dependencies WHERE project_id = ? AND depends_on_project_id = ?)"),
                projectId, dependencyId
            )
            if (checkResult is Result.Success && checkResult.value) {
                call.setProjectDependencyId(dependencyId)
            } else if ((checkResult is Result.Success && !checkResult.value) || (checkResult is Result.Failure && checkResult.error is DatabaseFailure.NotFound)) {
                call.respond(HttpStatusCode.NotFound, "Project dependency with ID $dependencyId does not exist for the project")
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

private suspend fun ensureNotificationExists(userId: Int, notificationId: Int) = ensureParamEntityExists(
    SafeSQL.select("SELECT EXISTS(SELECT 1 FROM notifications WHERE id = ? AND user_id = ?)"),
    notificationId, userId
)

private suspend fun ensureInviteExists(inviteId: Int) = ensureParamEntityExists(
    SafeSQL.select("SELECT EXISTS(SELECT 1 FROM invites WHERE id = ?)"),
    inviteId
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