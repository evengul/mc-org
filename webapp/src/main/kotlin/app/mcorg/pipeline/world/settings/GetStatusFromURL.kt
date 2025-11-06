package app.mcorg.pipeline.world.settings

import app.mcorg.pipeline.world.invitations.InvitationStatusFilter
import io.ktor.server.application.*

fun ApplicationCall.getStatusFromURL(): InvitationStatusFilter {
    return InvitationStatusFilter.fromApiName(this.request.queryParameters["status"]
        ?: this.request.headers["HX-Current-Url"]
            ?.substringAfter("status=")
            ?.substringBefore("&")
        ?: "pending") ?: InvitationStatusFilter.PENDING
}