package app.mcorg.pipeline.world.settings

import app.mcorg.pipeline.world.GetWorldInvitationsInput
import io.ktor.server.application.ApplicationCall

fun ApplicationCall.getStatusFromURL(): GetWorldInvitationsInput.StatusFilter {
    return when(this.request.queryParameters["status"]
        ?: this.request.headers["HX-Current-Url"]
            ?.substringAfter("status=")
            ?.substringBefore("&")
        ?: "pending") {
        "all" -> GetWorldInvitationsInput.StatusFilter.ALL
        "pending" -> GetWorldInvitationsInput.StatusFilter.PENDING
        "accepted" -> GetWorldInvitationsInput.StatusFilter.ACCEPTED
        "declined" -> GetWorldInvitationsInput.StatusFilter.DECLINED
        "expired" -> GetWorldInvitationsInput.StatusFilter.EXPIRED
        "cancelled" -> GetWorldInvitationsInput.StatusFilter.CANCELLED
        else -> GetWorldInvitationsInput.StatusFilter.PENDING
    }
}