package app.mcorg.pipeline.project.resources

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getProjectProductionItemId
import app.mcorg.presentation.utils.respondEmptyHtml
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

suspend fun ApplicationCall.handleDeleteProjectProductionItem() {
    val projectId = this.getProjectId()
    val productionItemId = this.getProjectProductionItemId()

    executePipeline(
        onSuccess = { respondEmptyHtml() },
        onFailure = { respond(HttpStatusCode.InternalServerError, "Failed to delete project production item") }
    ) {
        step(Step.value(projectId to productionItemId))
            .step(DeleteProjectProductionItemStep)
    }
}

private val DeleteProjectProductionItemStep = DatabaseSteps.update<Pair<Int, Int>, DatabaseFailure>(
    SafeSQL.delete("DELETE FROM project_productions WHERE id = ? AND project_id = ?"),
    parameterSetter = { statement, (projectId, productionItemId) ->
        statement.setInt(1, productionItemId)
        statement.setInt(2, projectId)
    },
    errorMapper = { it }
)