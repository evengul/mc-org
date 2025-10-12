package app.mcorg.pipeline.world.settings

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.world.GetWorldInvitationsInput
import app.mcorg.pipeline.world.worldInvitationsStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.settings.worldInvitations
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.html.stream.createHTML
import kotlinx.html.ul


suspend fun ApplicationCall.handleGetInvitationListFragment() {
    val worldId = this.getWorldId()

    val statusFilter = when (request.queryParameters["status"]) {
        "all" -> GetWorldInvitationsInput.StatusFilter.ALL
        "pending" -> GetWorldInvitationsInput.StatusFilter.PENDING
        "accepted" -> GetWorldInvitationsInput.StatusFilter.ACCEPTED
        "declined" -> GetWorldInvitationsInput.StatusFilter.DECLINED
        "expired" -> GetWorldInvitationsInput.StatusFilter.EXPIRED
        "cancelled" -> GetWorldInvitationsInput.StatusFilter.CANCELLED
        else -> GetWorldInvitationsInput.StatusFilter.PENDING // Default to PENDING if not specified or invalid
    }

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().ul { worldInvitations(it) })
        },
        onFailure = {
            respond(HttpStatusCode.InternalServerError, "Failed to load invitations")
        }
    ) {
        step(Step.value(GetWorldInvitationsInput(
            worldId = worldId,
            statusFilter = statusFilter
        ))).step(worldInvitationsStep)
    }
}