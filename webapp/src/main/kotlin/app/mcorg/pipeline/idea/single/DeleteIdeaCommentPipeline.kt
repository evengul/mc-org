package app.mcorg.pipeline.idea.single

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.utils.getIdeaCommentId
import app.mcorg.presentation.utils.respondEmptyHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleDeleteIdeaComment() {
    val ideaCommentId = this.getIdeaCommentId()

    executePipeline(
        onSuccess = {
            respondEmptyHtml()
        },
        onFailure = {
            respond(HttpStatusCode.InternalServerError, "Failed to delete idea comment")
        }
    ) {
        step(Step.value(ideaCommentId))
            .step(deleteIdeaStep)
    }
}

private val deleteIdeaStep = DatabaseSteps.update<Int>(
    sql = SafeSQL.delete("DELETE FROM idea_comments WHERE id = ?"),
    parameterSetter = { ps, input -> ps.setInt(1, input) }
)