package app.mcorg.presentation.plugins

import app.mcorg.config.AppConfig
import app.mcorg.config.CacheManager
import app.mcorg.domain.Production
import app.mcorg.domain.isDemoUserInProduction
import app.mcorg.domain.model.user.Role
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.auth.commonsteps.ConvertTokenStep
import app.mcorg.pipeline.auth.commonsteps.GetTokenStep
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.consts.ISSUER
import app.mcorg.presentation.templated.error.bannedPage
import app.mcorg.presentation.utils.getIdeaCommentId
import app.mcorg.presentation.utils.getIdeaId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

val AdminPlugin = createRouteScopedPlugin("AdminPlugin") {
    onCall {
        if (!it.getUser().isSuperAdmin) {
            it.respond(HttpStatusCode.NotFound)
        }
    }
}

val WorldAdminPlugin = createRouteScopedPlugin("WorldAdminPlugin") {
    onCall {
        val user = it.getUser()
        val worldId = it.getWorldId()

        val result = ValidateWorldMemberRole<Unit>(user, Role.ADMIN, worldId).process(Unit)
        if (result is Result.Failure && result.error is AppFailure.AuthError.NotAuthorized) {
            it.respond(HttpStatusCode.Forbidden, "You don't have permission to access this world.")
        }
    }
}

/**
 * Verifies the authenticated user is a member of the path world — ANY role (owner, admin,
 * or plain member), just not banned from it. `Role.MEMBER` is the lowest non-banned role
 * level, so `world_role <= MEMBER.level` in [ValidateWorldMemberRole] admits every
 * membership row and rejects both non-members and banned members. Use this to close IDOR
 * gaps on routes that only need "is a participant in this world", not a specific privilege
 * tier (compare [WorldAdminPlugin] / [WorldOwnerPlugin] for elevated-role gates).
 */
val WorldParticipantPlugin = createRouteScopedPlugin("WorldParticipantPlugin") {
    onCall {
        val user = it.getUser()
        val worldId = it.getWorldId()

        val result = ValidateWorldMemberRole<Unit>(user, Role.MEMBER, worldId).process(Unit)
        if (result is Result.Failure && result.error is AppFailure.AuthError.NotAuthorized) {
            it.respond(HttpStatusCode.Forbidden, "You don't have permission to access this world.")
        }
    }
}

val WorldOwnerPlugin = createRouteScopedPlugin("WorldOwnerPlugin") {
    onCall {
        val user = it.getUser()
        val worldId = it.getWorldId()

        val result = ValidateWorldMemberRole<Unit>(user, Role.OWNER, worldId).process(Unit)
        if (result is Result.Failure && result.error is AppFailure.AuthError.NotAuthorized) {
            it.respond(HttpStatusCode.Forbidden, "Only the world owner can perform this action.")
        }
    }
}

val BannedPlugin = createRouteScopedPlugin("BannedPlugin") {
    onCall {
        val userId = runCatching { it.getUser().id }.getOrNull() ?: return@onCall

        // Check cache first
        val cached = CacheManager.bannedUsers.getIfPresent(userId)
        if (cached != null) {
            if (cached) {
                it.respondHtml(bannedPage(), HttpStatusCode.Forbidden)
            }
            return@onCall
        }

        // Cache miss - query DB
        val result = DatabaseSteps.query<Int, Boolean>(
            sql = SafeSQL.select("SELECT 1 FROM global_user_roles where user_id = ? AND role = 'banned'"),
            parameterSetter = { statement, _ -> statement.setInt(1, userId) },
            resultMapper = { rs -> rs.next() }
        ).process(userId)

        if (result is Result.Success) {
            CacheManager.bannedUsers.put(userId, result.value)
            if (result.value) {
                it.respondHtml(bannedPage(), HttpStatusCode.Forbidden)
            }
        }
    }
}

/**
 * Gates mutation of an idea comment to the people entitled to it: its author, the owner of the
 * idea it sits under, or a superadmin.
 *
 * Before MCO-351 there was no check at all — `DELETE FROM idea_comments WHERE id = ?` was the
 * entire authorization story, and the Delete button being hidden unless `commenterId == userId`
 * was the only thing standing between the hub and a loop that wiped its whole comment and rating
 * history.
 *
 * Deliberately a plugin rather than a step inside the pipeline: mc-web's rule is that
 * authorization is visible from the route tree. Install it after [IdeaCommentParamPlugin], which
 * establishes that the comment is reachable under this idea in the first place.
 */
val IdeaCommentAuthorPlugin = createRouteScopedPlugin("IdeaCommentAuthorPlugin") {
    onCall { call ->
        val user = call.getUser()
        if (user.isSuperAdmin) return@onCall

        val commentId = call.getIdeaCommentId()
        val ideaId = call.getIdeaId()

        val permitted = DatabaseSteps.query<Unit, Boolean>(
            sql = SafeSQL.select(
                """
                SELECT EXISTS(
                    SELECT 1 FROM idea_comments c
                    JOIN ideas i ON i.id = c.idea_id
                    WHERE c.id = ? AND c.idea_id = ? AND (c.commenter_id = ? OR i.created_by = ?)
                )
                """.trimIndent()
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, commentId)
                statement.setInt(2, ideaId)
                statement.setInt(3, user.id)
                statement.setInt(4, user.id)
            },
            resultMapper = { rs -> rs.next() && rs.getBoolean(1) },
        ).process(Unit)

        // Fails closed: a database error denies rather than admits.
        if (permitted !is Result.Success || !permitted.value) {
            call.respond(HttpStatusCode.Forbidden, "You can only delete your own comments.")
        }
    }
}

val DemoUserPlugin = createRouteScopedPlugin("DemoUserPlugin") {
    onCall {
        if (AppConfig.env == Production) {
            val user = GetTokenStep(AUTH_COOKIE)
                .process(it.request.cookies)
                .getOrNull()
                ?.let { token ->
                    ConvertTokenStep(ISSUER)
                        .process(token)
                        .getOrNull()
                }
            if (user !== null && user.isDemoUserInProduction() && it.request.httpMethod !in listOf(HttpMethod.Get, HttpMethod.Options)) {
                val logger = LoggerFactory.getLogger("DemoUserPlugin")
                // path(), not uri() (MCO-339): uri includes the query string, path does not.
                logger.warn("Blocked ${it.request.httpMethod} request from demo user '${user.minecraftUsername}' to ${it.request.path()}")
                it.respond(HttpStatusCode.Forbidden, "Demo users are not allowed to ${it.request.httpMethod} requests.")
            }
        }
    }
}
