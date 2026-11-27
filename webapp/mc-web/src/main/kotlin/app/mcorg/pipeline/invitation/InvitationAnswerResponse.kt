package app.mcorg.pipeline.invitation

import app.mcorg.domain.model.invite.Invite
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.world.World
import app.mcorg.pipeline.PipelineScope
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.invitation.commonsteps.GetUserInvitationsStep
import app.mcorg.pipeline.minecraftfiles.GetSupportedVersionsStep
import app.mcorg.pipeline.world.commonsteps.GetPermittedWorldsInput
import app.mcorg.pipeline.world.commonsteps.GetPermittedWorldsStep
import app.mcorg.pipeline.world.commonsteps.GetWorldProjectPeekStep
import app.mcorg.pipeline.world.commonsteps.WorldProjectPeek
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.dsl.ALERT_CONTAINER_ID
import app.mcorg.presentation.templated.dsl.AlertType
import app.mcorg.presentation.templated.dsl.createAlert
import app.mcorg.presentation.templated.dsl.pages.worldsContent
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.stream.createHTML
import kotlinx.html.ul

/** What the Worlds page shows the invitee once they have answered an invitation. */
data class InvitationAnswered(
    val worlds: List<World>,
    val invitations: List<Invite>,
    val heroPeek: List<WorldProjectPeek>,
)

/** Read after the answer is written, so a joined world is in the list and the answered invite is not. */
suspend fun PipelineScope<AppFailure>.loadInvitationAnswered(userId: Int): InvitationAnswered {
    val worlds = GetPermittedWorldsStep.run(GetPermittedWorldsInput(userId = userId))
    val invitations = GetUserInvitationsStep.run(userId)
    val heroPeek = worlds.firstOrNull()
        ?.let { GetWorldProjectPeekStep().run(it.id) }
        ?: emptyList()
    return InvitationAnswered(worlds, invitations, heroPeek)
}

/**
 * The answer to Accept or Decline: the whole of `#worlds-content`, which the row's buttons target,
 * plus a toast. Re-rendering the list rather than removing the row is what makes a joined world
 * appear at once, swaps the empty state for the list on a first world, and drops the invitations
 * section when its last row goes.
 */
suspend fun invitationAnsweredHtml(user: TokenProfile, answered: InvitationAnswered, alertId: String, title: String): String {
    val supportedVersions = GetSupportedVersionsStep.getSupportedVersions()
    val content = createHTML(prettyPrint = false).div {
        id = "worlds-content"
        worldsContent(user, answered.worlds, supportedVersions, answered.invitations, answered.heroPeek)
    }
    val toast = createHTML(prettyPrint = false).ul {
        hxOutOfBands("beforeend:#$ALERT_CONTAINER_ID")
        li { createAlert(id = alertId, type = AlertType.SUCCESS, title = title) }
    }
    return content + toast
}
