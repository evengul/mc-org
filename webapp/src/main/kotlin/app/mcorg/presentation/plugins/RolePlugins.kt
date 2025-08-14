package app.mcorg.presentation.plugins

import app.mcorg.presentation.utils.getUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond

val AdminPlugin = createRouteScopedPlugin("AdminPlugin") {
    onCall {
        if (!it.getUser().isSuperAdmin) {
            it.respond(HttpStatusCode.Forbidden)
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