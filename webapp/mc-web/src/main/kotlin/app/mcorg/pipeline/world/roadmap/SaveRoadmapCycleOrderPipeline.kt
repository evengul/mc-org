package app.mcorg.pipeline.world.roadmap

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getWorldId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond

/**
 * Records which of two mutually-supplying projects comes first (MCO-460).
 *
 * The pair is a genuine loop — both edges derived from real demand, both true — so no rule can
 * pick a winner and the page asks. Saving means "set aside the first project's demand on the
 * second", which is a **subtraction** from the derived graph and never an addition: an ordering
 * someone wants to *assert* is a `project_dependencies` row (MCO-302), a different thing.
 *
 * Both surfaces inherit the answer, because both read
 * [GetFarmSupplyEdgesStep][app.mcorg.pipeline.project.commonsteps.GetFarmSupplyEdgesStep] —
 * the roadmap stops drawing the edge, and the project page stops calling it a prerequisite.
 * Keeping the rule in one query is what stops the two disagreeing again, which is the bug
 * MCO-461 was filed for.
 *
 * Authorization is the route's: this sits inside the world block behind `WorldAdminPlugin`,
 * because sequencing the world's projects is a world-level decision rather than a per-viewer
 * preference — the next person to open the roadmap sees the same order.
 */
suspend fun ApplicationCall.handleSaveRoadmapCycleOrder() {
    val worldId = getWorldId()
    val (first, waiting) = readPair() ?: return

    val result = DatabaseSteps.update<Unit>(
        sql = SafeSQL.insert("""
            INSERT INTO roadmap_cycle_order (world_id, consumer_project_id, producer_project_id)
            VALUES (?, ?, ?)
            ON CONFLICT (consumer_project_id, producer_project_id) DO NOTHING
        """.trimIndent()),
        parameterSetter = { statement, _ ->
            statement.setInt(1, worldId)
            statement.setInt(2, first)
            statement.setInt(3, waiting)
        }
    ).process(Unit)

    if (result is Result.Failure) return defaultHandleError(result.error)

    // Choosing a direction has to clear the opposite one, or the pair ends up with both edges
    // set aside and no ordering at all — the loop would vanish instead of being resolved.
    val opposite = DatabaseSteps.update<Unit>(
        sql = SafeSQL.delete("""
            DELETE FROM roadmap_cycle_order
            WHERE consumer_project_id = ? AND producer_project_id = ?
        """.trimIndent()),
        parameterSetter = { statement, _ ->
            statement.setInt(1, waiting)
            statement.setInt(2, first)
        }
    ).process(Unit)

    if (opposite is Result.Failure) return defaultHandleError(opposite.error)

    backToRoadmap(worldId)
}

/**
 * Forgets an ordering, putting the loop back as an open question (MCO-460).
 *
 * Undo rather than a toggle: the answer is a claim about the world, and taking it back should
 * restore the question rather than silently assert the reverse.
 */
suspend fun ApplicationCall.handleClearRoadmapCycleOrder() {
    val worldId = getWorldId()
    val (first, waiting) = readPair() ?: return

    val result = DatabaseSteps.update<Unit>(
        sql = SafeSQL.delete("""
            DELETE FROM roadmap_cycle_order
            WHERE world_id = ? AND consumer_project_id = ? AND producer_project_id = ?
        """.trimIndent()),
        parameterSetter = { statement, _ ->
            statement.setInt(1, worldId)
            statement.setInt(2, first)
            statement.setInt(3, waiting)
        }
    ).process(Unit)

    if (result is Result.Failure) return defaultHandleError(result.error)

    backToRoadmap(worldId)
}

/**
 * The two project ids the form posts, or null after responding with why not.
 *
 * The projects are not checked against the world here: the `world_id` column is set from the
 * route, and both id columns are foreign keys, so the worst a forged pair can do is store a
 * suppression for an edge that does not exist — which changes nothing, because the edge query
 * only ever subtracts.
 */
private suspend fun ApplicationCall.readPair(): Pair<Int, Int>? {
    val submitted = receiveParameters()
    val first = submitted["first"]?.toIntOrNull()
    val waiting = submitted["waiting"]?.toIntOrNull()

    if (first == null || waiting == null || first == waiting) {
        defaultHandleError(
            AppFailure.customValidationError("first", "Pick which of the two projects comes first")
        )
        return null
    }
    return first to waiting
}

private suspend fun ApplicationCall.backToRoadmap(worldId: Int) {
    val target = "/worlds/$worldId/roadmap"
    if (request.headers["HX-Request"] == "true") {
        clientRedirect(target)
    } else {
        response.headers.append("Location", target)
        respond(HttpStatusCode.SeeOther, "")
    }
}
