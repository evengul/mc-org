package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.invitation.commonsteps.GetUserInvitationsStep
import app.mcorg.pipeline.minecraftfiles.GetSupportedVersionsStep
import app.mcorg.pipeline.world.commonsteps.GetPermittedWorldsInput
import app.mcorg.pipeline.world.commonsteps.GetPermittedWorldsStep
import app.mcorg.pipeline.world.commonsteps.GetWorldProjectPeekStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.pages.worldsContent
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.stream.createHTML

/**
 * Flips this user's pin on a world. Pin is per (user, world), stored on world_members;
 * pinned worlds sort above the rest on the Worlds page. Re-renders #worlds-content so
 * the newly (un)pinned world moves to/from the top and the hero updates.
 */
data class ToggleWorldPinStep(val userId: Int) : Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<Int>(
            SafeSQL.update(
                "UPDATE world_members SET pinned = NOT pinned WHERE user_id = ? AND world_id = ?"
            ),
            parameterSetter = { statement, worldId ->
                statement.setInt(1, userId)
                statement.setInt(2, worldId)
            }
        ).process(input)
    }
}

suspend fun ApplicationCall.handleTogglePin() {
    val user = getUser()
    val worldId = getWorldId()

    handlePipeline(
        onSuccess = { (worlds, invitations, heroPeek) ->
            val supportedVersions = GetSupportedVersionsStep.getSupportedVersions()
            respondHtml(createHTML().div {
                id = "worlds-content"
                worldsContent(user, worlds, supportedVersions, invitations, heroPeek)
            })
        }
    ) {
        ToggleWorldPinStep(user.id).run(worldId)
        val worlds = GetPermittedWorldsStep.run(GetPermittedWorldsInput(userId = user.id))
        val invitations = GetUserInvitationsStep.run(user.id)
        val heroPeek = worlds.firstOrNull()
            ?.let { GetWorldProjectPeekStep().run(it.id) }
            ?: emptyList()
        Triple(worlds, invitations, heroPeek)
    }
}
