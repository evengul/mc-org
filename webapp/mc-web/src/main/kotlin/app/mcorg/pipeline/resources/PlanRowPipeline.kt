package app.mcorg.pipeline.resources

import app.mcorg.domain.model.world.World
import app.mcorg.engine.plan.Activity
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.resources.commonsteps.GetProgressForProjectStep
import app.mcorg.presentation.templated.dsl.pages.completedActivity
import app.mcorg.presentation.templated.dsl.pages.workRowCollapsedHtml
import app.mcorg.presentation.templated.dsl.pages.workRowStateOf
import app.mcorg.presentation.templated.dsl.pages.workRowStripHtml
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.util.getOrFail
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * GET /worlds/{worldId}/projects/{projectId}/plan/row/{itemId}?working=true|false
 *
 * Returns one work row in the form asked for: the 40px collapsed line, or the expanded strip with
 * the counters on it.
 *
 * **This is what "the working set is view state" means in practice.** Pressing Log does not write
 * anything — it asks for the same line rendered differently and swaps it in place, so what you are
 * working lives in the DOM and dies with the tab. That is the right lifetime for it today: a
 * project has no owner, so a persisted "in progress" would be one player's session leaking into
 * everyone else's page, and a stale one from three days ago is worse than none. When work has an
 * owner this becomes a stored per-user set and the endpoint keeps its shape.
 *
 * Rendering the strip on demand rather than hiding one inside all 634 rows is also the whole
 * point of the redesign — a hidden strip per row is the wall again, just invisible.
 */
suspend fun ApplicationCall.handleGetPlanRow() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val itemId = URLDecoder.decode(parameters.getOrFail("itemId"), StandardCharsets.UTF_8)
    val working = request.queryParameters["working"]?.toBooleanStrictOrNull() ?: false

    val plan: GatheringPlan? =
        when (val r = GenerateGatheringPlanStep.process(GatheringPlanInput(projectId, worldId))) {
            is Result.Success -> r.value
            is Result.Failure -> null
        }
    if (plan == null) {
        // Nothing to render against. 404 rather than an empty swap: HTMX leaves the stale row
        // alone, which beats blanking it.
        respond(HttpStatusCode.NotFound)
        return
    }

    val progress = when (val r = GetProgressForProjectStep.process(projectId)) {
        is Result.Success -> r.value
        is Result.Failure -> emptyMap()
    }

    // A finished line has left the plan — fully-collected targets are dropped before planning —
    // so it is rebuilt as done. Without this, Reopen on a completed line 404s.
    val activity: Activity = plan.activityList.firstOrNull { it.item.id == itemId }
        ?: completedActivity(
            itemId = itemId,
            itemName = when (val r = GetItemNameStep.process(ItemNameInput(worldId, itemId))) {
                is Result.Success -> r.value
                is Result.Failure -> null
            } ?: itemId.substringAfterLast(':').replace('_', ' ')
                .replaceFirstChar { it.uppercaseChar() },
            required = (progress[itemId] ?: 0).toLong(),
        )
    val farmScaleThreshold = when (val r = GetFarmScaleThresholdStep.process(worldId)) {
        is Result.Success -> r.value
        is Result.Failure -> World.DEFAULT_FARM_SCALE_THRESHOLD
    }
    // A dismissed item carries no badge (MCO-407), and this endpoint re-renders one row at a
    // time — miss it here and the badge comes back the first time the row is swapped.
    val farmScaleIds = FarmScaleDemands.itemIdsIn(plan, farmScaleThreshold, farmDismissalsFor(worldId).itemIds())

    val state = workRowStateOf(
        activity = activity,
        progress = progress,
        nodeIngredients = buildNodeIngredients(plan),
        feedsLabels = buildFeedsLabels(plan),
        farmScaleIds = farmScaleIds,
    )

    respondHtml(
        if (working) workRowStripHtml(worldId, projectId, state)
        else workRowCollapsedHtml(worldId, projectId, state)
    )
}
