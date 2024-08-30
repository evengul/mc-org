package app.mcorg.presentation.v2.handler

import app.mcorg.presentation.v2.configuration.usersApi
import app.mcorg.presentation.v2.router.utils.getUserFromCookie
import app.mcorg.presentation.v2.router.utils.removeTokenAndSignOut
import com.auth0.jwt.exceptions.TokenExpiredException
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetLanding() {
    val user = try {
        getUserFromCookie()
    } catch (e: TokenExpiredException) {
        removeTokenAndSignOut()
        return
    }

    if (user == null) {
        respondRedirect("/auth/signin")
    } else {
        val profile = usersApi.getProfile(user.id) ?: return removeTokenAndSignOut()
        if (profile.selectedWorld != null) {
            respondRedirect("/app/worlds/${profile.selectedWorld}/projects")
            return
        }
        respondRedirect("/app/worlds/add")
    }
}