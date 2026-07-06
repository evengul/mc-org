package app.mcorg.pipeline.project

import app.mcorg.config.CacheManager
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.Link
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import io.ktor.server.application.*

// Authorization for this route is enforced by WorldAdminPlugin, installed on the DELETE
// method in AppRouterV2/WorldHandler routing — not here. See the project rule: auth lives
// in Ktor plugins at the route level, never inside pipelines.
suspend fun ApplicationCall.handleDeleteProject() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()

    handlePipeline(
        onSuccess = { clientRedirect(Link.Worlds.world(worldId).projects().to) }
    ) {
        handleDeleteProjectStep.run(projectId)
        CacheManager.onProjectDeleted(worldId, projectId)
    }
}

private val handleDeleteProjectStep = DatabaseSteps.update<Int>(
    SafeSQL.delete("DELETE FROM projects WHERE id = ?"),
    parameterSetter = { statement, input -> statement.setInt(1, input) }
)
