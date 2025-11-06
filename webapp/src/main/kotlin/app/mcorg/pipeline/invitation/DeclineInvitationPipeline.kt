package app.mcorg.pipeline.invitation

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.invitation.commonsteps.ValidateInvitationAccessStep
import app.mcorg.pipeline.invitation.commonsteps.ValidateInvitationPendingStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.utils.getInviteId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleDeclineInvitation() {
    val user = this.getUser()
    val inviteId = this.getInviteId()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().div {
                div("notice notice--success") {
                    +"Invitation declined successfully"
                }
                div {
                    id = "invite-${inviteId}"
                }
            })
        },
        onFailure = { respond(HttpStatusCode.InternalServerError) }
    ) {
        value(inviteId)
            .step(ValidateInvitationAccessStep(user.id))
            .map { inviteId to inviteId }
            .step(ValidateInvitationPendingStep())
            .step(DeclineInvitationStep(user.id))
    }
}

data class DeclineInvitationStep(val userId: Int) : Step<Int, AppFailure, Unit> {
    override suspend fun process(input: Int): Result<AppFailure, Unit> {

        return DatabaseSteps.update<Int>(
            sql = SafeSQL.update("UPDATE invites SET status = 'DECLINED', status_reached_at = CURRENT_TIMESTAMP WHERE id = ?"),
            parameterSetter = { stmt, inviteId ->
                stmt.setInt(1, inviteId)
            }
        ).process(input).map {  }
    }
}