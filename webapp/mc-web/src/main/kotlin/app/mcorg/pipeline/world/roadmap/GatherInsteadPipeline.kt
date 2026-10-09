package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.utils.getWorldId
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters

/**
 * `gather instead ▸` on a TO BUILD row (MCO-574): take a farm still to build out of the plan.
 *
 * Nothing about the farm's items changes: a plan is supplied by DONE farms only, so they were on
 * the hand list all along. What the decision removes is the *promise* — the farm stops being a
 * prerequisite of anything, stops waiting on anything, and its share of each final project's hand
 * list stops reading as promised. All of that follows from [GetFarmSupplyEdgesStep][app.mcorg.pipeline.project.commonsteps.GetFarmSupplyEdgesStep]
 * and [GetWorldRoadMapStep] reading `project_gather_instead`; plan derivation never does, so a
 * re-derived plan cannot undo it.
 *
 * Admin-only, beside `cycle-order`: it changes the roadmap everyone in the world opens.
 */
suspend fun ApplicationCall.handleGatherInstead() {
    val worldId = getWorldId()
    val projectId = readProject() ?: return

    // One statement checks what the button promises: a project of this world, still to build, that
    // makes something. Asking again is not an error, so a conflict counts as the one row it already
    // is — the no-op update is what makes "0 rows" mean "not a farm you could gather instead of".
    val stored = DatabaseSteps.update<Unit>(
        sql = SafeSQL.insert("""
            INSERT INTO project_gather_instead (project_id)
            SELECT p.id
            FROM projects p
            WHERE p.id = ?
              AND p.world_id = ?
              AND p.state IN (?, ?, ?)
              AND EXISTS (SELECT 1 FROM project_supplied_items s WHERE s.project_id = p.id)
            ON CONFLICT (project_id) DO UPDATE SET created_at = project_gather_instead.created_at
        """.trimIndent()),
        parameterSetter = { statement, _ ->
            statement.setInt(1, projectId)
            statement.setInt(2, worldId)
            statement.setString(3, ProjectState.PENDING.name)
            statement.setString(4, ProjectState.ACTIVE.name)
            statement.setString(5, ProjectState.PAUSED.name)
        }
    ).process(Unit)

    when (stored) {
        is Result.Failure -> return defaultHandleError(stored.error)
        is Result.Success -> if (stored.value == 0) {
            return defaultHandleError(
                AppFailure.customValidationError("project", "Only a farm still to build can be gathered instead")
            )
        }
    }

    backToRoadmap(worldId)
}

/**
 * `build it after all ▸`: the farm is back in the plan, promising what it makes. Nothing to undo is
 * not an error — the roadmap already shows what the user asked for.
 */
suspend fun ApplicationCall.handleClearGatherInstead() {
    val worldId = getWorldId()
    val projectId = readProject() ?: return

    val result = DatabaseSteps.update<Unit>(
        sql = SafeSQL.delete("""
            DELETE FROM project_gather_instead g
            USING projects p
            WHERE p.id = g.project_id
              AND g.project_id = ?
              AND p.world_id = ?
        """.trimIndent()),
        parameterSetter = { statement, _ ->
            statement.setInt(1, projectId)
            statement.setInt(2, worldId)
        }
    ).process(Unit)

    if (result is Result.Failure) return defaultHandleError(result.error)

    backToRoadmap(worldId)
}

private suspend fun ApplicationCall.readProject(): Int? {
    val projectId = receiveParameters()["project"]?.toIntOrNull()
    if (projectId == null) {
        defaultHandleError(AppFailure.customValidationError("project", "Pick a farm"))
    }
    return projectId
}
