package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.world.ManualOrdering
import app.mcorg.domain.model.world.OrderingCandidate
import app.mcorg.domain.model.world.OrderingSource
import app.mcorg.pipeline.world.roadmap.ordering.OrderingField
import app.mcorg.presentation.hxDeleteWithConfirm
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxInclude
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTargetError
import app.mcorg.presentation.hxTrigger
import kotlinx.html.ButtonType
import kotlinx.html.FORM
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.textArea

/**
 * The roadmap's roster of hand-made orderings — see every one in a world, add one, take one
 * away (MCO-302, design § 4).
 *
 * **Why the section exists.** Nearly every edge on this page is *generated*: a farm produces
 * what a project needs, and the edge falls out of the data. The remainder are orderings a
 * person asserted — "dig the hole before the frame goes in" — which carry no material and so
 * can never be derived. They need a home, and the roadmap is the only surface where you can
 * see that a single hand-made edge is what makes the graph four layers deep instead of two.
 *
 * **The affordance is unconditional, and that is the point.** The first attempt at this
 * feature rendered the editor only once a row already existed, so the only way to reach it
 * was to already have an ordering — which only an idea import could give you, i.e. exactly
 * the state the issue exists to escape.
 *
 * The form sits *above* the list deliberately: it opens directly beneath the button that
 * triggered it, and the orderings already recorded stay visible below as reference.
 */
fun FlowContent.manualOrderingSection(
    worldId: Int,
    orderings: List<ManualOrdering>,
    generatedEdgeCount: Int,
    projectCount: Int,
    canEdit: Boolean,
) {
    div("rmg-section rmg-ordering") {
        id = "rmg-ordering"
        div("rmg-ordering__head") {
            span("rmg-label") { +"MANUAL ORDERING · ${orderings.size}" }
            span("rmg-note") {
                +if (generatedEdgeCount == 1) {
                    "1 edge is generated from farm output — these are the ones you added by hand"
                } else {
                    "$generatedEdgeCount edges are generated from farm output — " +
                        "these are the ones you added by hand"
                }
            }
            if (canEdit && projectCount >= 2) {
                button(classes = "btn btn--secondary btn--sm rmg-ordering__add") {
                    type = ButtonType.button
                    hxGet(formUrl(worldId))
                    hxTarget("#$FORM_ID")
                    hxSwap("outerHTML")
                    +"Add ordering"
                }
            }
        }

        // The collapsed form is an empty anchor, not a hidden form: it is swapped for the real
        // thing and swapped back, so there is only ever one place the form can be.
        orderingFormSlot()

        orderingList(worldId, orderings, canEdit)

        // Load-bearing. Without it people try to delete the generated supply edges from this
        // roster, and the real levers are somewhere else entirely.
        p("rmg-ordering__footer") {
            +"Removing a generated edge isn't possible here — change what the farm produces, "
            +"or what the project needs."
        }
    }
}

/** The form is addressed from three other files, so its id is the one worth naming. */
const val FORM_ID = "rmg-ordering-form"

// ---- the form ------------------------------------------------------------------------------

/** Everything the add/edit form renders from. One shape for both, so they cannot drift. */
data class OrderingFormState(
    val worldId: Int,
    val projectCount: Int,
    /** Set when editing an existing row; the pair is then fixed and only the reason is open. */
    val editingId: Int? = null,
    val firstId: Int? = null,
    val firstQuery: String = "",
    val thenId: Int? = null,
    val thenQuery: String = "",
    val reason: String = "",
    val error: String? = null,
)

/** The collapsed state: an anchor for the swap, and nothing else. */
fun FlowContent.orderingFormSlot() {
    div("rmg-ordering__slot") { id = FORM_ID }
}

fun orderingFormSlotFragment(): String = createHTML().div("rmg-ordering__slot") { id = FORM_ID }

/**
 * The add form, and — with [OrderingFormState.editingId] set — the same form with its pair
 * frozen.
 *
 * **Editing changes the reason, not the pair.** A different pair is a different edge (the
 * table's uniqueness constraint says so), so re-picking both projects would be remove-and-add
 * wearing a save button. The design left the update variant undrawn; this is the smallest
 * honest reading of it.
 *
 * Submitting is HTMX so a validation failure can come back into the form. Success answers with
 * a redirect instead of a fragment: a new ordering re-layers the whole graph, moving projects
 * the user never touched, so anything short of a page reload would leave the picture above
 * disagreeing with the roster below.
 */
fun orderingFormFragment(state: OrderingFormState): String =
    createHTML().form(classes = "rmg-ordering__form") {
        id = FORM_ID
        hxPost(
            state.editingId
                ?.let { "/worlds/${state.worldId}/roadmap/ordering/$it" }
                ?: "/worlds/${state.worldId}/roadmap/ordering"
        )
        hxTarget("#$FORM_ID")
        hxSwap("outerHTML")
        // A rejected submit comes back as this same form with the complaint at the top, so the
        // error response has to swap where the success one would. Without this the
        // response-targets extension leaves a 400 on the floor and the button looks dead.
        hxTargetError("#$FORM_ID")
        orderingFormFields(state)
    }

private fun FORM.orderingFormFields(state: OrderingFormState) {
    val worldId = state.worldId

    state.error?.let { message ->
        p("rmg-ordering__error") { +message }
    }

    div("rmg-ordering__col") {
        if (state.editingId != null) {
            span("rmg-ordering__field-label") { +"THE ORDERING" }
            div("rmg-ordering__fixed-pair") {
                span { +state.firstQuery }
                span("rmg-ordering__arrow") { +"┄▸" }
                span { +state.thenQuery }
            }
            hiddenInput { name = "firstId"; value = state.firstId?.toString().orEmpty() }
            hiddenInput { name = "thenId"; value = state.thenId?.toString().orEmpty() }
            p("rmg-ordering__help") {
                +"To point this ordering at other projects, remove it and add the one you meant."
            }
        } else {
            // Labelled by ORDER, never by role. "Dependency" and "dependent" are a coin flip
            // under pressure, and reversing them silently inverts the graph with no feedback.
            orderingCombobox(worldId, OrderingField.FIRST, "DO THIS FIRST", state)
            orderingCombobox(worldId, OrderingField.THEN, "BEFORE THIS", state)
        }
    }

    div("rmg-ordering__col") {
        span("rmg-ordering__field-label") { +"WHY — REQUIRED" }
        textArea(classes = "form-control rmg-ordering__reason") {
            name = "reason"
            attributes["required"] = "required"
            // The placeholder is --text-muted, not --text-disabled: required-field guidance is
            // substantive content, and the disabled ink fails contrast at 2.42:1.
            placeholder = "No material passes between these — say what does."
            +state.reason
        }
        if (state.editingId == null) {
            p("rmg-ordering__help") {
                +"Both fields search all ${state.projectCount} projects, done or not."
            }
        }
    }

    div("rmg-ordering__col rmg-ordering__actions") {
        button(classes = "btn btn--primary btn--sm") {
            type = ButtonType.submit
            +if (state.editingId != null) "Save changes" else "Add"
        }
        button(classes = "btn btn--secondary btn--sm") {
            type = ButtonType.button
            hxGet(formUrl(worldId, close = true))
            hxTarget("#$FORM_ID")
            hxSwap("outerHTML")
            +"Cancel"
        }
    }
}

/**
 * A project picker that searches every project in the world, in every state.
 *
 * A `select` would have been less work and wrong: a finished farm is a perfectly legitimate
 * prerequisite, so the list cannot be filtered to unbuilt projects, and at a few dozen entries
 * an unfiltered `select` is unusable. Hence a text field that queries the server.
 *
 * The picked value lives in a hidden input, so the visible text can say what was matched while
 * the form posts an id — a name is not unique, and this is a world that contains both *Slime
 * farm* and *New slime farm*.
 */
private fun FlowContent.orderingCombobox(
    worldId: Int,
    field: OrderingField,
    label: String,
    state: OrderingFormState,
) {
    val key = field.key
    val picked = if (field == OrderingField.FIRST) state.firstId else state.thenId
    val query = if (field == OrderingField.FIRST) state.firstQuery else state.thenQuery

    span("rmg-ordering__field-label") { +label }
    div("rmg-combo${if (picked != null) " rmg-combo--picked" else ""}") {
        input(type = InputType.text, classes = "form-control rmg-combo__input") {
            name = "${key}Query"
            value = query
            placeholder = "Search projects…"
            autoComplete = "off"
            attributes["aria-label"] = label
            hxGet("/worlds/$worldId/roadmap/ordering/search?field=$key")
            hxTarget("#rmg-combo-results-$key")
            hxSwap("innerHTML")
            hxInclude("closest form")
            hxTrigger("input changed delay:250ms, focus")
        }
        hiddenInput { name = "${key}Id"; value = picked?.toString().orEmpty() }
        div("rmg-combo__results") { id = "rmg-combo-results-$key" }
    }
}

/**
 * The results panel for one picker.
 *
 * Every row carries the project's state, because a world can hold both *Slime farm* and *New
 * slime farm* and picking the wrong one is undetectable afterwards.
 *
 * A row that would close a loop is shown and disabled rather than hidden. The old candidate
 * query excluded such projects, which is right for a closed dropdown and wrong for a search
 * box — the user types a name, gets nothing back, and retypes it. Marking at pick time also
 * says *which* end to reconsider, before a reason has been written.
 */
fun orderingResultsFragment(
    worldId: Int,
    field: OrderingField,
    matches: List<OrderingCandidate>,
    totalProjects: Int,
): String = createHTML().div("rmg-combo__panel") {
    if (matches.isEmpty()) {
        div("rmg-combo__row rmg-combo__row--note") { +"No project matches." }
        return@div
    }
    matches.forEach { candidate ->
        if (candidate.wouldCycle) {
            div("rmg-combo__row rmg-combo__row--blocked") {
                span("rmg-combo__name") { +candidate.name }
                span("rmg-combo__state rmg-tone-amber") { +"⚠ would cycle" }
            }
        } else {
            button(classes = "rmg-combo__row rmg-combo__row--pick") {
                type = ButtonType.button
                hxGet(
                    "/worlds/$worldId/roadmap/ordering/pick" +
                        "?field=${field.key}&projectId=${candidate.projectId}"
                )
                hxTarget("#$FORM_ID")
                hxSwap("outerHTML")
                hxInclude("closest form")
                span("rmg-combo__name") { +candidate.name }
                span("rmg-combo__state ${candidate.state.toneClass()}") { +candidate.state.label() }
            }
        }
    }
    div("rmg-combo__row rmg-combo__row--note") {
        +"${matches.size} of $totalProjects projects match"
    }
}

// ---- the list ------------------------------------------------------------------------------

private fun FlowContent.orderingList(
    worldId: Int,
    orderings: List<ManualOrdering>,
    canEdit: Boolean,
) {
    if (orderings.isEmpty()) {
        p("rmg-ordering__empty") {
            +"Nothing here yet — every edge on this page came from what your projects need."
        }
        return
    }

    div("rmg-ordering__list") {
        orderings.forEachIndexed { index, ordering ->
            div("rmg-ordering__row${if (index % 2 == 1) " rmg-ordering__row--alt" else ""}") {
                id = "rmg-ordering-row-${ordering.id}"
                div("rmg-ordering__pair") {
                    a(classes = "rmg-ordering__project") {
                        href = "/worlds/$worldId/projects/${ordering.firstProjectId}"
                        +ordering.firstProjectName
                    }
                    span("rmg-ordering__arrow") { +"┄▸" }
                    a(classes = "rmg-ordering__project") {
                        href = "/worlds/$worldId/projects/${ordering.thenProjectId}"
                        +ordering.thenProjectName
                    }
                }
                div("rmg-ordering__reason-text") {
                    val reason = ordering.reason
                    if (reason != null) {
                        +reason
                    } else {
                        // Rows the idea import wrote predate the required reason and cannot be
                        // given one retroactively. Saying so beats an empty cell, which reads
                        // as an explanation somebody forgot to type.
                        span("rmg-tone-disabled") {
                            +if (ordering.declaredBy == OrderingSource.IDEA_IMPORT) {
                                "Came in with an imported idea — no reason recorded."
                            } else {
                                "No reason recorded."
                            }
                        }
                    }
                }
                div("rmg-ordering__row-actions") {
                    if (canEdit) {
                        button(classes = "rmg-ordering__action rmg-ordering__action--edit") {
                            type = ButtonType.button
                            hxGet("/worlds/$worldId/roadmap/ordering/${ordering.id}/edit")
                            hxTarget("#$FORM_ID")
                            hxSwap("outerHTML")
                            +"edit"
                        }
                        button(classes = "rmg-ordering__action rmg-ordering__action--remove") {
                            type = ButtonType.button
                            hxDeleteWithConfirm(
                                url = "/worlds/$worldId/roadmap/ordering/${ordering.id}",
                                title = "Remove ordering",
                                description = "${ordering.firstProjectName} will no longer be " +
                                    "sequenced before ${ordering.thenProjectName}.",
                            )
                            // A removal can change how deep the world is, so the server answers
                            // with a redirect and the page reloads — header metadata, graph and
                            // roster move together. Target and swap are the fallback for a
                            // response that is a fragment instead, i.e. an error.
                            hxTarget("#rmg-ordering-row-${ordering.id}")
                            hxSwap("outerHTML")
                            +"remove"
                        }
                    }
                }
            }
        }
    }
}

// ---- shared --------------------------------------------------------------------------------

/** The query-string name of a picker, shared by the form, the search and the pick endpoints. */
val OrderingField.key: String
    get() = when (this) {
        OrderingField.FIRST -> "first"
        OrderingField.THEN -> "then"
    }

private fun formUrl(worldId: Int, close: Boolean = false): String =
    "/worlds/$worldId/roadmap/ordering/form" + if (close) "?close=true" else ""

/**
 * Every state says its own name, and the two that carry colour carry a glyph as well — the
 * primary user is red-green colour-blind, so colour is never the only cue.
 */
private fun ProjectState.label(): String = when (this) {
    ProjectState.PENDING -> "pending"
    ProjectState.ACTIVE -> "in progress"
    ProjectState.PAUSED -> "paused"
    ProjectState.DONE -> "✓ done"
    ProjectState.CANCELLED -> "✕ cancelled"
    ProjectState.ARCHIVED -> "archived"
}

private fun ProjectState.toneClass(): String = when (this) {
    ProjectState.DONE -> "rmg-tone-green"
    ProjectState.CANCELLED -> "rmg-tone-red"
    else -> "rmg-tone-muted"
}
