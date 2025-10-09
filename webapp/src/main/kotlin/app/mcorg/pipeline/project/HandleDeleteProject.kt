package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

private val handleDeleteProjectStep = DatabaseSteps.update<Int, DatabaseFailure>(
    SafeSQL.delete("DELETE FROM projects WHERE id = ?"),
    parameterSetter = { statement, input -> statement.setInt(1, input) },
    errorMapper = { it }
)

suspend fun ApplicationCall.handleDeleteProject() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()

    executePipeline(
        onFailure = { respond(HttpStatusCode.InternalServerError, "Failed to delete project.") },
        onSuccess = { clientRedirect("/app/worlds/$worldId") }
    ) {
        step(Step.value(projectId))
            .step(handleDeleteProjectStep)
    }
}