package app.mcorg.presentation.plugins

import app.mcorg.presentation.utils.getUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond

/**
 * Plugin to ensure user has idea_creator role.
 * Similar to AdminPlugin but checks for idea_creator permission.
 */
val IdeaCreatorPlugin = createRouteScopedPlugin("IdeaCreatorPlugin") {
    onCall {
        if (!it.getUser().isIdeaCreator) {
            it.respond(HttpStatusCode.Forbidden, "You don't have permission to create ideas.")
        }
    }
}

