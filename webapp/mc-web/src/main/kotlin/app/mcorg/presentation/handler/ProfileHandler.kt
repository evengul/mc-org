package app.mcorg.presentation.handler

import app.mcorg.pipeline.profile.handleGetProfile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.route

class ProfileHandler {
    fun Route.profileRoutes() {
        route("/profile") {
            get {
                call.handleGetProfile()
            }
            patch("/display-name") {
                // Update display name
            }
            patch("/avatar") {
                // Update avatar
            }
        }
    }
}



