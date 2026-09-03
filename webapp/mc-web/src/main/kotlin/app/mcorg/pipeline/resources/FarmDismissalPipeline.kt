package app.mcorg.pipeline.resources

import app.mcorg.pipeline.Result
import app.mcorg.pipeline.project.respondGatheringPlannerContent
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondBadRequest
import io.ktor.server.application.ApplicationCall
import io.ktor.server.util.getOrFail
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * POST /worlds/{worldId}/projects/{projectId}/farm-suggestions/dismissals/{itemId}
 *
 * Takes one item out of the "Worth a farm" panel for the whole world (MCO-407).
 *
 * The item id is a path segment because that is what is being addressed, and the label and the
 * quantity are read off the plan here rather than posted: a dismissal is a decision about an id,
 * and the two stored strings exist so the ignored list can name and date something that has
 * since left every plan. Taking them from the request would let the page write its own history.
 *
 * Authorization is the route's (`WorldAdminPlugin`), never this handler's — it changes what the
 * whole world sees, the same class of decision as the threshold it overrides.
 *
 * Answers with the whole plan fragment. A dismissal moves a line out of the roll-up, may remove
 * a design row with it, and clears the row's farm-scale badge further down the page; those are
 * one decision, and three separate out-of-band swaps of one decision is three chances to
 * disagree with each other.
 */
suspend fun ApplicationCall.handleDismissFarmSuggestion() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val itemId = URLDecoder.decode(parameters.getOrFail("itemId"), StandardCharsets.UTF_8)

    // The plan is the only honest source for what this item is called and how much of it the
    // project wants. Missing (the plan failed to derive, or the item just left it) is not an
    // error: the decision stands, and the ignored list falls back to the id.
    val activity = when (val r = GenerateGatheringPlanStep.process(GatheringPlanInput(projectId, worldId))) {
        is Result.Success -> r.value?.activityList?.firstOrNull { it.item.id == itemId }
        is Result.Failure -> null
    }

    val result = DismissFarmDemandStep.process(
        DismissFarmDemandInput(
            worldId = worldId,
            itemId = itemId,
            itemName = activity?.item?.name ?: itemId.substringAfterLast(':').replace('_', ' ')
                .replaceFirstChar { it.uppercaseChar() },
            quantity = activity?.quantity ?: 0L,
            dismissedBy = getUser().id,
        )
    )

    if (result is Result.Failure) {
        respondBadRequest("Could not dismiss this suggestion")
        return
    }

    respondGatheringPlannerContent()
}

/**
 * DELETE /worlds/{worldId}/projects/{projectId}/farm-suggestions/dismissals/{itemId}
 *
 * Puts a dismissed item back in the panel.
 *
 * A dismissal has no expiry and nothing revives it (see the migration), so this is the only way
 * back — which is why the ignored fold lists every dismissal the world has, not only the ones
 * this project's plan happens to want today.
 */
suspend fun ApplicationCall.handleRestoreFarmSuggestion() {
    val worldId = getWorldId()
    val itemId = URLDecoder.decode(parameters.getOrFail("itemId"), StandardCharsets.UTF_8)

    when (val result = RestoreFarmDemandStep.process(RestoreFarmDemandInput(worldId, itemId))) {
        is Result.Failure -> {
            defaultHandleError(result.error)
            return
        }

        is Result.Success -> respondGatheringPlannerContent()
    }
}
