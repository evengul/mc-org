package app.mcorg.presentation.plugins

import app.mcorg.config.CacheManager
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.setIdeaCommentId
import app.mcorg.presentation.utils.setIdeaId
import app.mcorg.presentation.utils.setInviteId
import app.mcorg.presentation.utils.setNotificationId
import app.mcorg.presentation.utils.setProjectDependencyId
import app.mcorg.presentation.utils.setProjectId
import app.mcorg.presentation.utils.setProjectProductionItemId
import app.mcorg.presentation.utils.setResourceGatheringId
import app.mcorg.presentation.utils.setTaskId
import app.mcorg.presentation.utils.setWorldId
import app.mcorg.presentation.utils.setWorldMemberId
import com.github.benmanes.caffeine.cache.Cache
import io.ktor.server.application.createRouteScopedPlugin

val WorldParamPlugin = createRouteScopedPlugin("WorldParamPlugin") {
    onCall { call ->
        val worldId = call.parameters["worldId"]?.toIntOrNull()
        if (worldId == null) {
            call.respondBadRequest("Invalid or missing world ID")
        } else {
            val checkResult = cachedEnsureExists(
                CacheManager.worldExists,
                worldId,
                SafeSQL.select("SELECT EXISTS(SELECT 1 FROM world WHERE id = ?)"),
                worldId
            )
            if (checkResult is Result.Success && checkResult.value) {
                call.setWorldId(worldId)
            } else if ((checkResult is Result.Success && !checkResult.value) || (checkResult is Result.Failure && checkResult.error is AppFailure.DatabaseError.NotFound)) {
                call.defaultHandleError(AppFailure.DatabaseError.NotFound)
            } else {
                call.defaultHandleError(checkResult.errorOrNull()!!)
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
            val cacheKey = "$worldId:$projectId"
            val checkResult = cachedEnsureExists(
                CacheManager.projectExists,
                cacheKey,
                SafeSQL.select("SELECT EXISTS(SELECT 1 FROM projects WHERE id = ? AND world_id = ?)"),
                projectId, worldId
            )
            if (checkResult is Result.Success && checkResult.value) {
                call.setProjectId(projectId)
            } else if ((checkResult is Result.Success && !checkResult.value) || (checkResult is Result.Failure && checkResult.error is AppFailure.DatabaseError.NotFound)) {
                call.defaultHandleError(AppFailure.DatabaseError.NotFound)
            } else {
                call.defaultHandleError(checkResult.errorOrNull()!!)
            }
        }
    }
}

val ActionTaskParamPlugin = createRouteScopedPlugin("TaskParamPlugin") {
    onCall { call ->
        val projectId = call.getProjectId()
        val taskId = call.parameters["taskId"]?.toIntOrNull()
        if (taskId == null) {
            call.respondBadRequest("Invalid or missing task ID")
        } else {
            val cacheKey = "$projectId:$taskId"
            val checkResult = cachedEnsureExists(
                CacheManager.taskExists,
                cacheKey,
                SafeSQL.select("SELECT EXISTS(SELECT 1 FROM action_task WHERE id = ? AND project_id = ?)"),
                taskId, projectId
            )
            if (checkResult is Result.Success && checkResult.value) {
                call.setTaskId(taskId)
            } else if ((checkResult is Result.Success && !checkResult.value) || (checkResult is Result.Failure && checkResult.error is AppFailure.DatabaseError.NotFound)) {
                call.defaultHandleError(AppFailure.DatabaseError.NotFound)
            } else {
                call.defaultHandleError(checkResult.errorOrNull()!!)
            }
        }
    }
}

val ResourceGatheringIdParamPlugin = createRouteScopedPlugin("ResourceGatheringIdParamPlugin") {
    onCall { call ->
        val projectId = call.getProjectId()
        val resourceGatheringId = call.parameters["resourceGatheringId"]?.toIntOrNull()
        if (resourceGatheringId == null) {
            call.respondBadRequest("Invalid or missing resource gathering ID")
        } else {
            val cacheKey = "$projectId:$resourceGatheringId"
            val checkResult = cachedEnsureExists(
                CacheManager.resourceGatheringExists,
                cacheKey,
                SafeSQL.select("SELECT EXISTS(SELECT 1 FROM resource_gathering WHERE id = ? AND project_id = ?)"),
                resourceGatheringId, projectId
            )
            if (checkResult is Result.Success && checkResult.value) {
                call.setResourceGatheringId(resourceGatheringId)
            } else if ((checkResult is Result.Success && !checkResult.value) || (checkResult is Result.Failure && checkResult.error is AppFailure.DatabaseError.NotFound)) {
                call.defaultHandleError(AppFailure.DatabaseError.NotFound)
            } else {
                call.defaultHandleError(checkResult.errorOrNull()!!)
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
            val cacheKey = "$userId:$notificationId"
            val checkResult = cachedEnsureExists(
                CacheManager.notificationExists,
                cacheKey,
                SafeSQL.select("SELECT EXISTS(SELECT 1 FROM notifications WHERE id = ? AND user_id = ?)"),
                notificationId, userId
            )
            if (checkResult is Result.Success && checkResult.value) {
                call.setNotificationId(notificationId)
            } else if ((checkResult is Result.Success && !checkResult.value) || (checkResult is Result.Failure && checkResult.error is AppFailure.DatabaseError.NotFound)) {
                call.defaultHandleError(AppFailure.DatabaseError.NotFound)
            } else {
                call.defaultHandleError(checkResult.errorOrNull()!!)
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
            val checkResult = cachedEnsureExists(
                CacheManager.inviteExists,
                inviteId,
                SafeSQL.select("SELECT EXISTS(SELECT 1 FROM invites WHERE id = ?)"),
                inviteId
            )
            if (checkResult is Result.Success && checkResult.value) {
                call.setInviteId(inviteId)
            } else if ((checkResult is Result.Success && !checkResult.value) || (checkResult is Result.Failure && checkResult.error is AppFailure.DatabaseError.NotFound)) {
                call.defaultHandleError(AppFailure.DatabaseError.NotFound)
            } else {
                call.defaultHandleError(checkResult.errorOrNull()!!)
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
            val cacheKey = "$memberId:$worldId"
            val checkResult = cachedEnsureExists(
                CacheManager.worldMemberExists,
                cacheKey,
                SafeSQL.select("SELECT EXISTS(SELECT 1 FROM world_members WHERE user_id = ? AND world_id = ?)"),
                memberId, worldId
            )
            if (checkResult is Result.Success && checkResult.value) {
                call.setWorldMemberId(memberId)
            } else if ((checkResult is Result.Success && !checkResult.value) || (checkResult is Result.Failure && checkResult.error is AppFailure.DatabaseError.NotFound)) {
                call.defaultHandleError(AppFailure.DatabaseError.NotFound)
            } else {
                call.defaultHandleError(checkResult.errorOrNull()!!)
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
            val cacheKey = "$itemId:$projectId"
            val checkResult = cachedEnsureExists(
                CacheManager.projectProductionItemExists,
                cacheKey,
                SafeSQL.select("SELECT EXISTS(SELECT 1 FROM project_productions WHERE id = ? AND project_id = ?)"),
                itemId, projectId
            )
            if (checkResult is Result.Success && checkResult.value) {
                call.setProjectProductionItemId(itemId)
            } else if ((checkResult is Result.Success && !checkResult.value) || (checkResult is Result.Failure && checkResult.error is AppFailure.DatabaseError.NotFound)) {
                call.defaultHandleError(AppFailure.DatabaseError.NotFound)
            } else {
                call.defaultHandleError(checkResult.errorOrNull()!!)
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
            val cacheKey = "$projectId:$dependencyId"
            val checkResult = cachedEnsureExists(
                CacheManager.projectDependencyExists,
                cacheKey,
                SafeSQL.select("SELECT EXISTS(SELECT 1 FROM project_dependencies WHERE project_id = ? AND depends_on_project_id = ?)"),
                projectId, dependencyId
            )
            if (checkResult is Result.Success && checkResult.value) {
                call.setProjectDependencyId(dependencyId)
            } else if ((checkResult is Result.Success && !checkResult.value) || (checkResult is Result.Failure && checkResult.error is AppFailure.DatabaseError.NotFound)) {
                call.defaultHandleError(AppFailure.DatabaseError.NotFound)
            } else {
                call.defaultHandleError(checkResult.errorOrNull()!!)
            }
        }
    }
}

val IdeaParamPlugin = createRouteScopedPlugin("IdeaParamPlugin") {
    onCall { call ->
        val ideaId = call.parameters["ideaId"]?.toIntOrNull()
        if (ideaId == null) {
            call.respondBadRequest("Invalid or missing idea ID")
        } else {
            val checkResult = cachedEnsureExists(
                CacheManager.ideaExists,
                ideaId,
                SafeSQL.select("SELECT EXISTS(SELECT 1 FROM ideas WHERE id = ?)"),
                ideaId
            )
            if (checkResult is Result.Success && checkResult.value) {
                call.setIdeaId(ideaId)
            } else if ((checkResult is Result.Success && !checkResult.value) || (checkResult is Result.Failure && checkResult.error is AppFailure.DatabaseError.NotFound)) {
                call.defaultHandleError(AppFailure.DatabaseError.NotFound)
            } else {
                call.defaultHandleError(checkResult.errorOrNull()!!)
            }
        }
    }
}

val IdeaCommentParamPlugin = createRouteScopedPlugin("IdeaCommentParamPlugin") {
    onCall { call ->
        val commentId = call.parameters["commentId"]?.toIntOrNull()
        if (commentId == null) {
            call.respondBadRequest("Invalid or missing idea comment ID")
        } else {
            val checkResult = cachedEnsureExists(
                CacheManager.ideaCommentExists,
                commentId,
                SafeSQL.select("SELECT EXISTS(SELECT 1 FROM idea_comments WHERE id = ?)"),
                commentId
            )
            if (checkResult is Result.Success && checkResult.value) {
                call.setIdeaCommentId(commentId)
            } else if ((checkResult is Result.Success && !checkResult.value) || (checkResult is Result.Failure && checkResult.error is AppFailure.DatabaseError.NotFound)) {
                call.defaultHandleError(AppFailure.DatabaseError.NotFound)
            } else {
                call.defaultHandleError(checkResult.errorOrNull()!!)
            }
        }
    }
}

/**
 * Check cache first for existence. On cache miss, query the database and populate the cache.
 * Only caches positive (exists=true) results from the DB to avoid caching deletions incorrectly.
 */
private suspend fun <K : Any> cachedEnsureExists(
    cache: Cache<K, Boolean>,
    cacheKey: K,
    sql: SafeSQL,
    vararg ids: Int
): Result<AppFailure.DatabaseError, Boolean> {
    // Cache hit
    cache.getIfPresent(cacheKey)?.let { return Result.success(it) }

    // Cache miss - query DB
    val step = DatabaseSteps.query<List<Int>, Boolean>(
        sql,
        { statement, input -> input.forEachIndexed { index, id -> statement.setInt(index + 1, id) } },
        resultMapper = { rs -> rs.next() && rs.getBoolean(1) }
    )
    val result = step.process(ids.toList())

    // Only cache true results (entity exists). False results could be due to
    // a race with creation, so we let those re-check on next request.
    if (result is Result.Success && result.value) {
        cache.put(cacheKey, true)
    }

    return result
}
