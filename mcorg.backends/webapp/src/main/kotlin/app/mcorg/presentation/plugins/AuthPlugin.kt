package app.mcorg.presentation.plugins

import app.mcorg.presentation.utils.getUserFromCookie
import app.mcorg.presentation.utils.removeTokenAndSignOut
import app.mcorg.presentation.utils.storeUser
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