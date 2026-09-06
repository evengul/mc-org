package app.mcorg.pipeline.world.roadmap.ordering

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.world.ManualOrdering
import app.mcorg.domain.model.world.OrderingCandidate
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.templated.dsl.pages.FORM_ID
import app.mcorg.presentation.templated.dsl.pages.OrderingFormState
import app.mcorg.presentation.templated.dsl.pages.key
import app.mcorg.presentation.templated.dsl.pages.orderingFormFragment
import app.mcorg.presentation.templated.dsl.pages.orderingFormSlotFragment
import app.mcorg.presentation.templated.dsl.pages.orderingResultsFragment
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.redirectClientOrBrowser
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters

/*
 * The manual-ordering editor behind the roadmap's § 4 roster (MCO-302).
 *
 * `project_dependencies` has existed since V2_7_0 with no writer but the idea import, which
 * meant an import could assert an ordering the application had no way to take back. These
 * handlers are that missing writer, and the delete is the half that actually unblocks people.
 *
 * **Authorization is the route's**, and it is `WorldAdminPlugin` — the same rule
 * `/roadmap/cycle-order` states: sequencing the world's projects changes the page for
 * everyone who opens it, so it is a world decision rather than a per-viewer preference.
 *
 * **Every mutation answers with a redirect, never a fragment.** A new or removed ordering
 * re-layers the entire graph: ranks change for projects the user never touched, the header's
 * layer count changes, and the callout under the graph appears or vanishes. Swapping one
 * section would leave the page disagreeing with itself.
 */

private const val REASON_MAX_LENGTH = 2000

// ---- opening and closing the form ------------------------------------------------------------

/** `GET .../ordering/form` — the open add form, or (with `close`) the collapsed anchor again. */
suspend fun ApplicationCall.handleGetOrderingForm() {
    val worldId = getWorldId()
    if (request.queryParameters["close"] == "true") {
        return respondHtml(orderingFormSlotFragment())
    }

    val candidates = candidatesOrNull(worldId, OrderingField.FIRST, null) ?: return
    respondHtml(
        orderingFormFragment(
            formStateOf(worldId, request.queryParameters, candidates.size)
        )
    )
}

/** `GET .../ordering/{id}/edit` — the same form with the pair frozen and the reason loaded. */
suspend fun ApplicationCall.handleGetOrderingEditForm() {
    val worldId = getWorldId()
    val ordering = orderingOrNull(worldId) ?: return
    val candidates = candidatesOrNull(worldId, OrderingField.FIRST, null) ?: return

    respondHtml(
        orderingFormFragment(
            OrderingFormState(
                worldId = worldId,
                projectCount = candidates.size,
                editingId = ordering.id,
                firstId = ordering.firstProjectId,
                firstQuery = ordering.firstProjectName,
                thenId = ordering.thenProjectId,
                thenQuery = ordering.thenProjectName,
                reason = ordering.reason.orEmpty(),
            )
        )
    )
}

// ---- the comboboxes --------------------------------------------------------------------------

/**
 * `GET .../ordering/search` — one picker's results panel.
 *
 * Matching happens here rather than in SQL: a world holds a few dozen projects, so the step
 * returns all of them and this filters. That keeps `LIKE` escaping out of the query and lets
 * the panel say "2 of 29 projects match" without a second count.
 */
suspend fun ApplicationCall.handleSearchOrderingCandidates() {
    val worldId = getWorldId()
    val params = request.queryParameters
    val field = fieldOrNull(params["field"]) ?: return respondBadRequest("Unknown field")

    // The counterpart is whatever the *other* picker currently holds — it decides which
    // direction a loop would close in.
    val counterpart = when (field) {
        OrderingField.FIRST -> params["thenId"]?.toIntOrNull()
        OrderingField.THEN -> params["firstId"]?.toIntOrNull()
    }
    val candidates = candidatesOrNull(worldId, field, counterpart) ?: return

    val query = params["${field.key}Query"].orEmpty().trim()
    val matches = if (query.isEmpty()) candidates else candidates.filter {
        it.name.contains(query, ignoreCase = true)
    }

    respondHtml(orderingResultsFragment(worldId, field, matches, candidates.size))
}

/** `GET .../ordering/pick` — re-renders the whole form with one picker filled in. */
suspend fun ApplicationCall.handlePickOrderingProject() {
    val worldId = getWorldId()
    val params = request.queryParameters
    val field = fieldOrNull(params["field"]) ?: return respondBadRequest("Unknown field")
    val projectId = params["projectId"]?.toIntOrNull()
        ?: return respondBadRequest("Unknown project")

    val candidates = candidatesOrNull(worldId, field, null) ?: return
    val picked = candidates.firstOrNull { it.projectId == projectId }
        ?: return respondBadRequest("That project is not in this world")

    val state = formStateOf(worldId, params, candidates.size)
    respondHtml(
        orderingFormFragment(
            when (field) {
                OrderingField.FIRST -> state.copy(firstId = picked.projectId, firstQuery = picked.name)
                OrderingField.THEN -> state.copy(thenId = picked.projectId, thenQuery = picked.name)
            }
        )
    )
}

// ---- writing -----------------------------------------------------------------------------------

/** `POST .../ordering` — record a new hand-made ordering. */
suspend fun ApplicationCall.handleAddManualOrdering() {
    val worldId = getWorldId()
    val submitted = receiveParameters()

    val candidates = candidatesOrNull(worldId, OrderingField.FIRST, null) ?: return
    val state = formStateOf(worldId, submitted, candidates.size)

    val reason = state.reason.trim()
    val firstId = state.firstId
    val thenId = state.thenId
    if (firstId == null || thenId == null) return respondWithFormError(state, "Pick both projects.")

    // Field order is the check order, so the message names the first thing that is wrong
    // rather than the last thing looked at.
    val complaint = when {
        firstId == thenId -> "A project cannot come before itself."
        candidates.none { it.projectId == firstId } || candidates.none { it.projectId == thenId } ->
            "Both projects have to belong to this world."
        reason.isEmpty() -> "Say why this ordering exists — nothing in the data can explain it later."
        reason.length > REASON_MAX_LENGTH -> "That reason is too long — keep it under $REASON_MAX_LENGTH characters."
        else -> null
    }
    if (complaint != null) return respondWithFormError(state, complaint)

    // Defence in depth behind the combobox's own marking: the results panel greys out the
    // rows that would close a loop, and this refuses one that arrives anyway.
    val forward = candidatesOrNull(worldId, OrderingField.THEN, firstId) ?: return
    if (forward.firstOrNull { it.projectId == thenId }?.wouldCycle == true) {
        return respondWithFormError(
            state,
            "That would make a loop — ${state.thenQuery} already has to happen before ${state.firstQuery}."
        )
    }

    val inserted = DatabaseSteps.update<Unit>(
        sql = SafeSQL.insert("""
            INSERT INTO project_dependencies (project_id, depends_on_project_id, reason, declared_by)
            VALUES (?, ?, ?, 'EDITOR')
            ON CONFLICT (project_id, depends_on_project_id) DO NOTHING
        """.trimIndent()),
        parameterSetter = { statement, _ ->
            statement.setInt(1, thenId)
            statement.setInt(2, firstId)
            statement.setString(3, reason)
        }
    ).process(Unit)

    when (inserted) {
        is Result.Failure -> return defaultHandleError(inserted.error)
        // `DO NOTHING` rather than a pre-check: the pair is unique in the table, so this both
        // reports the duplicate and closes the race a separate SELECT would leave open.
        is Result.Success -> if (inserted.value == 0) {
            return respondWithFormError(state, "Those two are already ordered.")
        }
    }

    CacheManager.onProjectDependencyCreated(thenId, firstId)
    backToRoadmap(worldId)
}

/**
 * `POST .../ordering/{id}` — rewrite an ordering's reason.
 *
 * The pair is not editable, so this takes no project ids. Pointing an ordering at different
 * projects is a different edge, which the table's own uniqueness constraint already says; the
 * form offers remove-and-add for that.
 *
 * A row the idea import wrote becomes an `EDITOR` row here, because somebody has now
 * explained it — which is the only difference between the two provenances.
 */
suspend fun ApplicationCall.handleUpdateManualOrdering() {
    val worldId = getWorldId()
    val submitted = receiveParameters()
    val ordering = orderingOrNull(worldId) ?: return
    val candidates = candidatesOrNull(worldId, OrderingField.FIRST, null) ?: return

    val reason = submitted["reason"].orEmpty().trim()
    val state = OrderingFormState(
        worldId = worldId,
        projectCount = candidates.size,
        editingId = ordering.id,
        firstId = ordering.firstProjectId,
        firstQuery = ordering.firstProjectName,
        thenId = ordering.thenProjectId,
        thenQuery = ordering.thenProjectName,
        reason = reason,
    )

    val complaint = when {
        reason.isEmpty() -> "Say why this ordering exists — nothing in the data can explain it later."
        reason.length > REASON_MAX_LENGTH -> "That reason is too long — keep it under $REASON_MAX_LENGTH characters."
        else -> null
    }
    if (complaint != null) return respondWithFormError(state, complaint)

    val updated = DatabaseSteps.update<Unit>(
        sql = SafeSQL.update("""
            UPDATE project_dependencies pd
            SET reason = ?, declared_by = 'EDITOR'
            FROM projects p
            WHERE p.id = pd.project_id
              AND p.world_id = ?
              AND pd.id = ?
        """.trimIndent()),
        parameterSetter = { statement, _ ->
            statement.setString(1, reason)
            statement.setInt(2, worldId)
            statement.setInt(3, ordering.id)
        }
    ).process(Unit)

    if (updated is Result.Failure) return defaultHandleError(updated.error)
    backToRoadmap(worldId)
}

/**
 * `DELETE .../ordering/{id}` — forget an ordering.
 *
 * This is the operation the issue exists for: until it landed, an idea import could assert a
 * dependency that nothing short of deleting the project could remove.
 */
suspend fun ApplicationCall.handleDeleteManualOrdering() {
    val worldId = getWorldId()
    val ordering = orderingOrNull(worldId) ?: return

    val deleted = DatabaseSteps.update<Unit>(
        sql = SafeSQL.delete("""
            DELETE FROM project_dependencies pd
            USING projects p
            WHERE p.id = pd.project_id
              AND p.world_id = ?
              AND pd.id = ?
        """.trimIndent()),
        parameterSetter = { statement, _ ->
            statement.setInt(1, worldId)
            statement.setInt(2, ordering.id)
        }
    ).process(Unit)

    if (deleted is Result.Failure) return defaultHandleError(deleted.error)

    CacheManager.onProjectDependencyDeleted(ordering.thenProjectId, ordering.firstProjectId)
    backToRoadmap(worldId)
}

// ---- shared -------------------------------------------------------------------------------------

/**
 * Both project ids and the reason as the form currently holds them.
 *
 * Read from whichever bag the request carries — query string for the GET fragments, the body
 * for a submit — so the form round-trips through `pick` without losing what has been typed.
 */
private fun formStateOf(worldId: Int, params: Parameters, projectCount: Int) = OrderingFormState(
    worldId = worldId,
    projectCount = projectCount,
    firstId = params["firstId"]?.toIntOrNull(),
    firstQuery = params["firstQuery"].orEmpty(),
    thenId = params["thenId"]?.toIntOrNull(),
    thenQuery = params["thenQuery"].orEmpty(),
    reason = params["reason"].orEmpty(),
)

private fun fieldOrNull(raw: String?): OrderingField? =
    OrderingField.entries.firstOrNull { it.key == raw }

/**
 * The world's projects with cycle marks, or null after answering with the failure.
 *
 * Doubles as the world's project count and as the membership check both writes need — every
 * row it returns is in this world by construction.
 */
private suspend fun ApplicationCall.candidatesOrNull(
    worldId: Int,
    field: OrderingField,
    counterpartId: Int?,
): List<OrderingCandidate>? {
    val result = GetOrderingCandidatesStep(worldId, field, counterpartId).process(Unit)
    if (result is Result.Failure) {
        defaultHandleError(result.error)
        return null
    }
    return (result as Result.Success).value
}

/**
 * The `{orderingId}` row, scoped to this world, or null after answering 404.
 *
 * Reads the whole roster and picks the row rather than fetching it by id: the roster is a
 * handful of rows, the world filter is already in that query, and reusing it means the roster
 * and every action on it can never disagree about which rows exist.
 */
private suspend fun ApplicationCall.orderingOrNull(worldId: Int): ManualOrdering? {
    val orderingId = parameters["orderingId"]?.toIntOrNull()
    if (orderingId == null) {
        defaultHandleError(AppFailure.DatabaseError.NotFound)
        return null
    }
    val result = GetManualOrderingsStep(worldId).process(Unit)
    if (result is Result.Failure) {
        defaultHandleError(result.error)
        return null
    }
    val ordering = (result as Result.Success).value.firstOrNull { it.id == orderingId }
    if (ordering == null) {
        defaultHandleError(AppFailure.DatabaseError.NotFound)
        return null
    }
    return ordering
}

/**
 * A rejected submit comes back as the form itself, with what is wrong at the top and every
 * field still filled in.
 *
 * `respondBadRequest` retargets the response at the form — the codebase's own idiom for
 * routing an error somewhere other than the triggering element's target.
 */
private suspend fun ApplicationCall.respondWithFormError(state: OrderingFormState, message: String) {
    respondBadRequest(
        errorHtml = orderingFormFragment(state.copy(error = message)),
        target = "#$FORM_ID",
        swap = "outerHTML",
    )
}

private suspend fun ApplicationCall.backToRoadmap(worldId: Int) {
    redirectClientOrBrowser("/worlds/$worldId/roadmap")
}
