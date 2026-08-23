package app.mcorg.pipeline.idea.single

import app.mcorg.config.CacheManager
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.idea.ideaCommentFormOob
import app.mcorg.presentation.templated.idea.ideaRatingDistributionOob
import app.mcorg.presentation.utils.getIdeaCommentId
import app.mcorg.presentation.utils.getIdeaId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*

suspend fun ApplicationCall.handleDeleteIdeaComment() {
    val ideaId = this.getIdeaId()
    val ideaCommentId = this.getIdeaCommentId()

    handlePipeline(
        onSuccess = { distribution ->
            respondHtml(
                ideaRatingDistributionOob(distribution.total, distribution.average, distribution.countPerStar) +
                ideaCommentFormOob(ideaId)
            )
        }
    ) {
        deleteIdeaCommentStep.run(ideaId to ideaCommentId)
        CacheManager.onIdeaCommentDeleted(ideaId, ideaCommentId)
        FetchRatingDistributionStep(ideaId).run(Unit)
    }
}

/**
 * Scoped by `idea_id` as well as `id` (MCO-351).
 *
 * [app.mcorg.presentation.plugins.IdeaCommentAuthorPlugin] on the route is the authorization;
 * this is the belt to its braces, so a comment can never be deleted through the wrong idea even
 * if that plugin is one day dropped from the route tree.
 */
private val deleteIdeaCommentStep = DatabaseSteps.update<Pair<Int, Int>>(
    sql = SafeSQL.delete("DELETE FROM idea_comments WHERE id = ? AND idea_id = ?"),
    parameterSetter = { ps, (ideaId, commentId) ->
        ps.setInt(1, commentId)
        ps.setInt(2, ideaId)
    }
)