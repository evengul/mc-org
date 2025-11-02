package app.mcorg.presentation.plugins

import app.mcorg.config.AppConfig
import app.mcorg.domain.Production
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.auth.commonsteps.ConvertTokenStep
import app.mcorg.pipeline.auth.commonsteps.GetTokenStep
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.pipeline.world.ValidateWorldMemberRoleFailure
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.consts.ISSUER
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respond
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

        val result = ValidateWorldMemberRole(user, Role.ADMIN).process(worldId)
        if (result is Result.Failure && result.error is ValidateWorldMemberRoleFailure.InsufficientPermissions) {
            it.respond(HttpStatusCode.Forbidden, "You don't have permission to access this world.")
        }
    }
}

val BannedPlugin = createRouteScopedPlugin("BannedPlugin") {
    onCall {
        val userId = it.getUser().id
        val result = DatabaseSteps.query<Int, DatabaseFailure, Boolean>(
            sql = SafeSQL.select("SELECT 1 FROM global_user_roles where user_id = ? AND role = 'banned'"),
            parameterSetter = { statement, _ -> statement.setInt(1, userId) },
            errorMapper = { e -> e },
            resultMapper = { rs -> rs.next() }
        ).process(userId)
        if (result is Result.Success && result.value) {
            it.respond(HttpStatusCode.Forbidden, "You are banned from accessing this application.")
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
            if (user !== null && user.isDemoUserInProduction && it.request.httpMethod !in listOf(HttpMethod.Get, HttpMethod.Options)) {
                val logger = LoggerFactory.getLogger("DemoUserPlugin")
                logger.warn("Blocked ${it.request.httpMethod} request from demo user '${user.minecraftUsername}' to ${it.request.uri}")
                it.respond(HttpStatusCode.Forbidden, "Demo users are not allowed to ${it.request.httpMethod} requests.")
            }
        }
    }
}