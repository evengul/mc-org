package app.mcorg.pipeline.world.settings.invitations

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.world.invitations.CountWorldInvitationsResult
import app.mcorg.pipeline.world.invitations.CountWorldInvitationsStep
import app.mcorg.pipeline.world.invitations.InvitationStatusFilter
import app.mcorg.pipeline.world.settings.getStatusFromURL
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.settings.worldInvitationTabs
import app.mcorg.presentation.utils.getInviteId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleCancelInvitation() {
    val worldId = this.getWorldId()
    val invitationId = this.getInviteId()

    val selectedStatus = this.getStatusFromURL()

    executePipeline(
        onSuccess = { result: CountWorldInvitationsResult ->
            val mainContent = createHTML().div {
                hxOutOfBands("true")
                hxTarget("#invitation-status-filter")
                worldInvitationTabs(result, selectedStatus)
            }
            if ((selectedStatus == InvitationStatusFilter.PENDING && result.pending == 0) ||
                (selectedStatus == InvitationStatusFilter.ALL && result.all == 0)) {
                respondHtml(
                    mainContent + createHTML().ul {
                        hxOutOfBands("afterbegin:ul.invitation-list")
                        li {
                            id = "empty-invitations-list"
                            p("subtle") {
                                +"No invitations found with this status."
                            }
                        }
                    }
                )
            } else {
                respondHtml(mainContent)
            }
        },
        onFailure = {
            respond(HttpStatusCode.InternalServerError, "Failed to cancel invitation")
        }
    ) {
        step(Step.value(worldId to invitationId))
            .step(cancelInviteStep)
            .map { }
            .step(CountWorldInvitationsStep(worldId))
    }
}

private val cancelInviteStep = DatabaseSteps.update<Pair<Int, Int>>(
    sql = SafeSQL.update("UPDATE invites SET status = 'CANCELLED', status_reached_at = NOW() WHERE id = ? AND world_id = ?"),
    parameterSetter = { statement, input ->
        statement.setInt(1, input.second)
        statement.setInt(2, input.first)
    }
)