package app.mcorg.presentation.handler.v2

import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import io.ktor.server.routing.route

class InviteHandler {
    fun Route.inviteRoutes() {
        route("/invites/{inviteId}") {
            patch("/accept") {
                // Accept the invite
            }
            patch("/decline") {
                // Decline the invite
            }
        }
    }
}