package app.mcorg.pipeline.world.settings.invitations

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.world.invitations.GetWorldInvitationsStep
import app.mcorg.pipeline.world.settings.getStatusFromURL
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.settings.worldInvitations
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.stream.createHTML
import kotlinx.html.ul


suspend fun ApplicationCall.handleGetInvitationListFragment() {
    val worldId = this.getWorldId()
    val statusFilter = this.getStatusFromURL()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().ul { worldInvitations(it) })
        }
    ) {
        step(Step.value(statusFilter))
            .step(GetWorldInvitationsStep(worldId))
    }
}