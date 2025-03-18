package app.mcorg.presentation.plugins

import app.mcorg.domain.model.permissions.Authority
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.*
import com.auth0.jwt.exceptions.TokenExpiredException
import io.ktor.server.application.*

val AuthPlugin = createRouteScopedPlugin("AuthPlugin") {
    onCall {
        try {
            val user = it.getUserFromCookie()
            if (user == null) {
                it.removeTokenAndSignOut()
            } else {
                it.storeUser(user)
            }
        } catch (e: TokenExpiredException) {
            it.removeTokenAndSignOut()
        }
    }
}

val WorldParticipantPlugin = createRouteScopedPlugin("WorldAccessPlugin") {
    onCall {
        val userId = it.getUserId()
        val worldId = it.getWorldId()
        val hasAccess = permissionsApi.hasWorldPermission(userId, Authority.PARTICIPANT, worldId)
        if (!hasAccess) {
            throw IllegalCallerException("User does not have access to world")
        }
    }
}

val WorldAdminPlugin = createRouteScopedPlugin("WorldAdminPlugin") {
    onCall {
        val userId = it.getUserId()
        val worldId = it.getWorldId()
        val hasAccess = permissionsApi.hasWorldPermission(userId, Authority.ADMIN, worldId)
        if (!hasAccess) {
            throw IllegalCallerException("User does not have admin access to world")
        }
    }
}