package app.mcorg.presentation.plugins

import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.pipeline.world.ValidateWorldMemberRoleFailure
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond

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
        if (it.getUser().isBanned) {
            it.respond(HttpStatusCode.Forbidden, "You are banned from accessing this application.")
        }
    }
}