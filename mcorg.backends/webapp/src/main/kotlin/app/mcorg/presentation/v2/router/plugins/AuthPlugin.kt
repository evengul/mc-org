package app.mcorg.presentation.v2.router.plugins

import app.mcorg.presentation.v2.router.utils.getUserFromCookie
import app.mcorg.presentation.v2.router.utils.removeTokenAndSignOut
import app.mcorg.presentation.v2.router.utils.storeUser
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