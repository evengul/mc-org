package app.mcorg.pipeline.project

import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.templated.dsl.Link
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond

/** The checkbox name on each suggested design in the plan's farm-scale section (MCO-459). */
const val SELECTED_DESIGN_FIELD = "design"

/**
 * Opens the batch review wizard for the designs ticked on a plan (MCO-459).
 *
 * This endpoint exists only because the wizard's first URL is not knowable until the form is
 * submitted — it names the first *selected* idea. A GET form cannot build that, and the
 * alternative is client-side URL assembly, which this codebase does not do for navigation.
 * So the form posts here and this hands back a redirect; it creates nothing and validates
 * nothing beyond the selection itself.
 *
 * Authorization is the route's, not this handler's: it sits inside the world/project block,
 * so `WorldParticipantPlugin` has already run. Each review step then re-checks ADMIN on the
 * world before showing anything, which is where the real gate has always been — this only
 * decides which URL to open.
 */
suspend fun ApplicationCall.handleStartFarmSuggestionImport() {
    val worldId = getWorldId()
    val projectId = getProjectId()

    val selected = receiveParameters()
        .getAll(SELECTED_DESIGN_FIELD)
        ?.mapNotNull { it.toIntOrNull() }
        ?.distinct()
        .orEmpty()

    if (selected.isEmpty()) {
        // Submitting with nothing ticked is a slip, not an error worth a page: the plan is
        // where the user already is and where the checkboxes are.
        return redirectTo(Link.Worlds.world(worldId).project(projectId).to)
    }

    val queue = ImportQueue(ideaIds = selected, returnToProjectId = projectId)
    redirectTo(queue.reviewHref(selected.first(), worldId))
}

/**
 * Sends the browser to [target].
 *
 * Mirrors [handleImportIdea]'s redirect exactly, and for the same reason: the plan's batch
 * form is a plain POST, so a real 303 is what a browser needs. The HX branch is there because
 * the fragment re-render paths (`PlanChainPipeline`) can render this section too, and an
 * `HX-Redirect` on a non-HTMX request would silently leave a blank page.
 */
private suspend fun ApplicationCall.redirectTo(target: String) {
    if (request.headers["HX-Request"] == "true") {
        clientRedirect(target)
    } else {
        response.headers.append("Location", target)
        respond(HttpStatusCode.SeeOther, "")
    }
}
