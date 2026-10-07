package app.mcorg.pipeline.idea.single

import app.mcorg.domain.model.idea.IdeaVisibility
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.handler.respondRefusal
import app.mcorg.presentation.templated.idea.ideaVisibilityControlFragment
import app.mcorg.presentation.utils.getIdeaId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.pipeline.idea.commonsteps.GetIdeaStep
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall

/**
 * PATCH /ideas/{ideaId}/public — move a private design onto the community hub (MCO-291).
 *
 * The route carries [app.mcorg.presentation.plugins.IdeaPublisherPlugin], so the caller is known to
 * hold the publishing role. Ownership is a separate question and is checked here, mirroring
 * `handleRevertIdeaToDraft`: holding the role lets you publish *your* designs, not anyone's.
 */
suspend fun ApplicationCall.handlePublishIdeaToHub() {
    val ideaId = getIdeaId()
    val user = getUser()

    val idea = when (val result = GetIdeaStep.process(ideaId)) {
        is Result.Failure -> { respondRefusal(HttpStatusCode.NotFound, "Not found", "That idea no longer exists.", alertId = "not-found-error"); return }
        is Result.Success -> result.value
    }
    if (idea.createdBy != user.id && !user.isSuperAdmin) {
        respondRefusal(HttpStatusCode.Forbidden, "Not Authorized", "Only the person who created this idea can publish it.", alertId = "not-authorized-error")
        return
    }

    handlePipeline(
        onSuccess = {
            respondHtml(ideaVisibilityControlFragment(idea.copy(visibility = IdeaVisibility.PUBLIC), user))
        }
    ) {
        PublishIdeaToHubStep.run(ideaId)
    }
}

private val PublishIdeaToHubStep = DatabaseSteps.update<Int>(
    sql = SafeSQL.update("UPDATE ideas SET visibility = 'PUBLIC', updated_at = NOW() WHERE id = ?"),
    parameterSetter = { statement, ideaId -> statement.setInt(1, ideaId) }
)
