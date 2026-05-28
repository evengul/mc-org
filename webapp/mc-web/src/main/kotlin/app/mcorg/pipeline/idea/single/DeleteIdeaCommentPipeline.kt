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
        deleteIdeaStep.run(ideaCommentId)
        CacheManager.onIdeaCommentDeleted(ideaCommentId)
        FetchRatingDistributionStep(ideaId).run(Unit)
    }
}

private val deleteIdeaStep = DatabaseSteps.update<Int>(
    sql = SafeSQL.delete("DELETE FROM idea_comments WHERE id = ?"),
    parameterSetter = { ps, input -> ps.setInt(1, input) }
)