package app.mcorg.pipeline.project

import app.mcorg.config.CacheManager
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.resources.invalidateDemandSuppliedBy
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
        // Before the delete, not after: the productions this reads are about to cascade away
        // with the project, and after that nothing can tell which items stopped being supplied
        // (MCO-404). Unconditional on state — a farm that was never DONE invalidates nothing,
        // because its items were never in anyone's supply.
        invalidateDemandSuppliedBy(worldId, projectId)
        handleDeleteProjectStep.run(projectId)
        CacheManager.onProjectDeleted(worldId, projectId)
    }
}

private val handleDeleteProjectStep = DatabaseSteps.update<Int>(
    SafeSQL.delete("DELETE FROM projects WHERE id = ?"),
    parameterSetter = { statement, input -> statement.setInt(1, input) }
)
