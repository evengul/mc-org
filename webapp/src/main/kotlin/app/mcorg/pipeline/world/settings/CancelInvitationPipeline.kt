package app.mcorg.pipeline.world.settings

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.world.GetWorldInvitationsInput
import app.mcorg.pipeline.world.worldInvitationsCountStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.settings.worldInvitationTabs
import app.mcorg.presentation.utils.getInviteId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.html.ul

suspend fun ApplicationCall.handleCancelInvitation() {
    val worldId = this.getWorldId()
    val invitationId = this.getInviteId()

    val selectedStatus = this.getStatusFromURL()

    executePipeline(
        onSuccess = {
            val mainContent = createHTML().div {
                hxOutOfBands("true")
                hxTarget("#invitation-status-filter")
                worldInvitationTabs(it, selectedStatus)
            }
            if ((selectedStatus == GetWorldInvitationsInput.StatusFilter.PENDING && it[GetWorldInvitationsInput.StatusFilter.PENDING] == 0) ||
                (selectedStatus == GetWorldInvitationsInput.StatusFilter.ALL && it[GetWorldInvitationsInput.StatusFilter.ALL] == 0)) {
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
            .step(Step.value(GetWorldInvitationsInput(
                worldId = worldId,
                statusFilter = GetWorldInvitationsInput.StatusFilter.PENDING
            )))
            .step(worldInvitationsCountStep)
    }
}

private val cancelInviteStep = DatabaseSteps.update<Pair<Int, Int>, DatabaseFailure>(
    sql = SafeSQL.update("UPDATE invites SET status = 'CANCELLED', status_reached_at = NOW() WHERE id = ? AND world_id = ?"),
    parameterSetter = { statement, input ->
        statement.setInt(1, input.second)
        statement.setInt(2, input.first)
    },
    errorMapper = { it }
)