package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.pipeline.Result
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.templated.dsl.pages.RecommendedAnswer
import app.mcorg.presentation.templated.dsl.pages.bulkAnswerControl
import app.mcorg.presentation.templated.dsl.pages.bulkAnswerToastDismissal
import app.mcorg.presentation.templated.dsl.pages.bulkAnswerUndoToast
import app.mcorg.presentation.templated.dsl.pages.foldedAttentionQuestions
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondEmptyHtml
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

/**
 * MCO-507 — answering the folded tail of small questions in one action.
 *
 * Three endpoints, all under `/worlds/{worldId}/projects/{projectId}/plan/attention/bulk`:
 *
 *   GET     the control (the offer, the picks it would make, the button) — lazy-loaded into the
 *           "Needs attention" section so the preview and the write are computed by *one* piece of
 *           server code. A template that computed its own preview could disagree with what the
 *           POST then applies, and the whole action rests on it not doing that.
 *   POST    apply them, as ordinary tag-member overrides.
 *   DELETE  revert exactly the rows a POST created (`?ids=`).
 *
 * Authorization is the route's, not this file's: these sit inside the world-participant gate in
 * `WorldHandler` with the rest of the plan endpoints.
 */

/**
 * Below this many folded questions there is no offer.
 *
 * Zero is obvious. One is the interesting case and the answer is still no: the action would cost a
 * disclosure, a read and a click to replace a click, and it would put a decision behind a bulk
 * affordance when there is no bulk. The fold's own [foldedAttentionQuestions] rule already never
 * hides fewer than three, so this is a floor rather than a second policy.
 */
private const val MIN_BULK_QUESTIONS = 2

/**
 * The folded questions paired with the member the picker would recommend, in the order the section
 * shows them.
 *
 * Empty whenever anything is missing — no plan, no ingested graph, a tag with no rankable member.
 * A question with no recommendation is dropped rather than guessed at; it stays a question.
 */
private suspend fun bulkRecommendations(worldId: Int, projectId: Int): List<RecommendedAnswer> {
    val plan = deriveOrNull(projectId, worldId) ?: return emptyList()
    val graph = getGraphForWorld(worldId) ?: return emptyList()

    return foldedAttentionQuestions(plan.activityList).mapNotNull { activity ->
        val tag = activity.item as? MinecraftTag ?: return@mapNotNull null
        // The same demand the picker scores against, found the same way, so the button's pick is
        // the option the picker marks "best score ★".
        val demand = plan.drillTreeFor(tag.id)?.let { findNodeById(it, tag.id) }?.quantityIfAlone
            ?: activity.quantity
        val member = TagMemberRanking.recommended(graph, tag.content, demand) ?: return@mapNotNull null
        RecommendedAnswer(
            tagId = tag.id,
            tagName = tag.name,
            memberId = member.id,
            memberName = member.name,
        )
    }
}

/**
 * GET /worlds/{worldId}/projects/{projectId}/plan/attention/bulk
 *
 * The control fragment, or an empty body when there is nothing to offer — the section's slot then
 * simply stays empty rather than rendering an affordance with no picks behind it.
 */
suspend fun ApplicationCall.handleGetBulkAnswerControl() {
    val worldId = getWorldId()
    val projectId = getProjectId()

    val picks = bulkRecommendations(worldId, projectId)
    if (picks.size < MIN_BULK_QUESTIONS) {
        respondEmptyHtml()
        return
    }
    respondHtml(bulkAnswerControl(worldId, projectId, picks))
}

/**
 * POST /worlds/{worldId}/projects/{projectId}/plan/attention/bulk
 *
 * Applies the recommendation for every folded question as an ordinary tag-member override, and
 * re-renders the List lens with an undo toast swapped in out-of-band.
 *
 * The questions are recomputed here rather than taken from the request: a page that has been open
 * a while may be describing a fold that no longer exists, and an action that answered a *lead*
 * question because the client said so would be exactly the failure this design avoids.
 *
 * `plannerPick` is set to the same member that is being written — by construction these rows agree
 * with the recommendation, which is what makes them readable later as "took the offer" rather than
 * as corrections (MCO-506).
 */
suspend fun ApplicationCall.handleBulkAnswerFoldedQuestions() {
    val worldId = getWorldId()
    val projectId = getProjectId()

    val picks = bulkRecommendations(worldId, projectId)
    if (picks.size < MIN_BULK_QUESTIONS) {
        respondBadRequest(
            buildValidationError("questions", "There are no folded questions left to answer."),
            target = "#error-message",
            swap = "innerHTML",
        )
        return
    }

    val createdIds = mutableListOf<Int>()
    for (pick in picks) {
        val choice = PlanOverride.TagMember(
            itemId = pick.tagId,
            memberItemId = pick.memberId,
            plannerPick = pick.memberId,
        )
        when (val r = UpsertPlanOverrideStep(projectId).process(choice)) {
            is Result.Success -> createdIds.add(r.value)
            is Result.Failure -> {
                defaultHandleError(r.error)
                return
            }
        }
    }

    val fragment = listRerenderFragment(worldId, projectId) ?: return
    respondHtml(fragment + bulkAnswerUndoToast(worldId, projectId, createdIds))
}

/**
 * DELETE /worlds/{worldId}/projects/{projectId}/plan/attention/bulk?ids=1,2,3
 *
 * Reverts exactly the rows the POST created. By row id, because "revert those N" has to leave
 * alone anything the user answered themselves, and only identity can promise that — the delete is
 * additionally scoped to this project and to live rows in [DeletePlanOverridesByIdStep].
 */
suspend fun ApplicationCall.handleUndoBulkAnswer() {
    val worldId = getWorldId()
    val projectId = getProjectId()

    val ids = request.queryParameters["ids"].orEmpty()
        .split(',')
        .mapNotNull { it.trim().toIntOrNull() }

    if (ids.isEmpty()) {
        respondBadRequest("Missing or unreadable required query parameter: ids")
        return
    }

    when (val r = DeletePlanOverridesByIdStep(projectId).process(ids)) {
        is Result.Failure -> {
            defaultHandleError(r.error)
            return
        }
        is Result.Success -> { /* continue */ }
    }

    val fragment = listRerenderFragment(worldId, projectId) ?: return
    respondHtml(fragment + bulkAnswerToastDismissal())
}
