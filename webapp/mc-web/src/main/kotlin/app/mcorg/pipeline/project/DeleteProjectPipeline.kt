package app.mcorg.pipeline.project

import app.mcorg.config.CacheManager
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.resources.InvalidateDemandSuppliedByStep
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
        onSuccess = { clientRedirect(Link.Worlds.world(worldId).roadmap().to) }
    ) {
        DeleteProjectStep(worldId).run(projectId)
        CacheManager.onProjectDeleted(worldId, projectId)
    }
}

/**
 * Deletes a project, invalidating the stored demand its productions supplied first.
 *
 * Before the delete, not after: the productions the invalidation reads cascade away with the
 * project, and after that nothing can tell which items stopped being supplied (MCO-404). In the
 * same transaction, so the invalidation and the delete commit together: committed first on its
 * own, a derivation starting in between would read the bumped generation with the farm still in
 * its supply, and store that plan as current (MCO-584). Unconditional on state — a farm that was
 * never DONE supplied nothing, so its item join matches no one.
 */
internal data class DeleteProjectStep(val worldId: Int) : Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> =
        DatabaseSteps.transaction { connection ->
            Step<Int, AppFailure.DatabaseError, Int> { projectId ->
                when (val invalidated = InvalidateDemandSuppliedByStep(worldId, projectId, connection).process(Unit)) {
                    is Result.Failure -> invalidated
                    is Result.Success -> DatabaseSteps.update<Int>(
                        SafeSQL.delete("DELETE FROM projects WHERE id = ?"),
                        parameterSetter = { statement, id -> statement.setInt(1, id) },
                        transactionConnection = connection,
                    ).process(projectId)
                }
            }
        }.process(input)
}
