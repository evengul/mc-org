package app.mcorg.presentation.handler

import app.mcorg.pipeline.invitation.handleAcceptInvitation
import app.mcorg.pipeline.invitation.handleDeclineInvitation
import app.mcorg.presentation.plugins.InviteParamPlugin
import io.ktor.server.routing.*

class InviteHandler {
    fun Route.inviteRoutes() {
        route("/invites/{inviteId}") {
            install(InviteParamPlugin)
            patch("/accept") {
                call.handleAcceptInvitation()
            }
            patch("/decline") {
                call.handleDeclineInvitation()
            }
        }
    }
}