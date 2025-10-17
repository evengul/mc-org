package app.mcorg.pipeline.idea.single

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.utils.getIdeaCommentId
import app.mcorg.presentation.utils.respondEmptyHtml
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

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

private val deleteIdeaStep = DatabaseSteps.update<Int, DatabaseFailure>(
    sql = SafeSQL.delete("DELETE FROM idea_comments WHERE id = ?"),
    parameterSetter = { ps, input -> ps.setInt(1, input) },
    errorMapper = { it }
)