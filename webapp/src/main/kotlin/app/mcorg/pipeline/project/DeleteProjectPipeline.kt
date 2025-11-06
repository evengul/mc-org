package app.mcorg.pipeline.project

import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleDeleteProject() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val user = this.getUser()

    val access = ValidateWorldMemberRole<Unit>(user, Role.ADMIN, worldId).process(Unit)

    if (access is Result.Failure) {
        respond(HttpStatusCode.Forbidden, "You don't have permission to delete this world.")
    }

    executePipeline(
        onFailure = { respond(HttpStatusCode.InternalServerError, "Failed to delete project.") },
        onSuccess = { clientRedirect("/app/worlds/$worldId") }
    ) {
        value(projectId)
            .step(handleDeleteProjectStep)
    }
}

private val handleDeleteProjectStep = DatabaseSteps.update<Int>(
    SafeSQL.delete("DELETE FROM projects WHERE id = ?"),
    parameterSetter = { statement, input -> statement.setInt(1, input) }
)