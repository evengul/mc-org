package app.mcorg.pipeline.invitation

import app.mcorg.config.CacheManager
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.invitation.commonsteps.ValidateInvitationAccessStep
import app.mcorg.pipeline.invitation.commonsteps.ValidateInvitationPendingStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.utils.getInviteId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleDeclineInvitation() {
    val user = this.getUser()
    val inviteId = this.getInviteId()

    handlePipeline(
        onSuccess = {
            respondHtml(createHTML().div {
                div("notice notice--success") {
                    +"Invitation declined successfully"
                }
                div {
                    id = "invite-${inviteId}"
                }
            })
        }
    ) {
        ValidateInvitationAccessStep(user.id).run(inviteId)
        ValidateInvitationPendingStep<Int>().run(inviteId to inviteId)
        DeclineInvitationStep(user.id).run(inviteId)
        CacheManager.onInviteChanged(inviteId)
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
