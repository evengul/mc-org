package app.mcorg.pipeline.invitation

import app.mcorg.config.CacheManager
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.Step
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

suspend fun ApplicationCall.handleDeclineInvitation() {
    val user = this.getUser()
    val inviteId = this.getInviteId()

    handlePipeline(
        onSuccess = { (worldName, answered) ->
            respondHtml(
                invitationAnsweredHtml(user, answered, "invitation-declined-$inviteId", "Declined invitation to $worldName")
            )
        }
    ) {
        val access = ValidateInvitationAccessStep(user.id).run(inviteId)
        val worldName = access.second
        ValidateInvitationPendingStep<Int>().run(inviteId to inviteId)
        DeclineInvitationStep(user.id).run(inviteId)
        CacheManager.onInviteChanged(inviteId)
        worldName to loadInvitationAnswered(user.id)
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
