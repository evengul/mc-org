package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.engine.plan.ActivityGroup
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.Activity
import app.mcorg.pipeline.resources.FeedsLabel
import app.mcorg.engine.plan.SupplySource
import kotlinx.html.BUTTON
import kotlinx.html.DIV
import kotlinx.html.stream.createHTML
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.span
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * A work section's rows, rebuilt around one idea: **the counters belong to the line you are
 * actually working, not to all 634 of them.**
 *
 * The old row gave every line the same weight and the same six steppers — `-1728 -64 -1 +1 +64
 * +1728` on a line needing 3 wool as readily as on one needing 111,005 planks. That control block
 * was most of a row's 64–101px, which is how a project page reached 48,000px. A collapsed row here
 * is 40px and carries no steppers at all; pressing **Log** swaps it for a work strip that has
 * them.
 *
 * Any number of lines can be open at once. The working set is **per-device view state**, held in
 * the DOM rather than the database: it is "what am I doing this session", and a stale *in
 * progress* from three days ago would be noise. It becomes server state when work has an owner.
 *
 * Farm-supplied lines are ordinary lines here. They used to be a separate passive band, on the
 * reasoning that a farm solves the item — but emptying a farm is still a trip that takes time, and
 * the only real difference is where the material comes from. That is a label, not a layout.
 */

/**
 * A stand-in for a line that has finished.
 *
 * `GenerateGatheringPlanStep` drops fully-collected targets before planning, so the moment you
 * log the last item the activity disappears from the plan — and a row swap that looks it up finds
 * nothing. Rendering nothing deletes the row out from under the user, which is what happened the
 * first time a chip was ticked. The caller still knows the item and what it needed, so the line
 * is rebuilt from that and renders as done.
 */
internal fun completedActivity(itemId: String, itemName: String, required: Long): Activity =
    Activity(
        item = Item(id = itemId, name = itemName),
        quantity = required,
        crafts = 1,
        leftover = 0,
        status = PlanNodeStatus.RESOLVED,
        group = ActivityGroup.OTHER,
    )

/** Item / stack / double-shulker — the units players already count in. */
private val STEPS = intArrayOf(-1728, -64, -1, 1, 64, 1728)

/** How a line stands, derived rather than stored (see the file KDoc on the deferred override). */
internal data class WorkRowState(
    val activity: Activity,
    val have: Long,
    val isFarmScale: Boolean,
    val sourceLabel: String?,
    val feeds: FeedsLabel?,
) {
    val need: Long get() = activity.quantity

    /**
     * Whether the material comes from something that already makes it, rather than from a trip
     * you plan yourself. Changes the *label* on the line and nothing else.
     *
     * It used to suppress the counter as well, on MCO-403's reasoning that "a supplied item
     * terminates its chain, so a collected number would count toward a finish line the planner
     * does not have and would never mark the row complete". The first half is true and the second
     * does not follow: the row already prints a demand — 74,557 Cobblestone, 50,000 Gunpowder —
     * and that *is* the finish line. Hauling fifty thousand gunpowder out of a farm is a dozen
     * trips, and it is exactly the work a counter exists to track. What MCO-403 actually
     * described is the planner having nothing to re-derive as you haul, which was never a reason
     * to stop counting.
     */
    val isSupplied: Boolean get() = activity.status == PlanNodeStatus.SUPPLIED
    val remaining: Long get() = (need - have).coerceAtLeast(0)
    val done: Boolean get() = need > 0 && have >= need
    val percent: Int get() = if (need > 0) ((have * 100) / need).coerceIn(0, 100).toInt() else 0
    val slug: String get() = activity.item.id.replace(":", "-")
    val rowId: String get() = "plan-activity-$slug"
    val encodedItemId: String get() = URLEncoder.encode(activity.item.id, StandardCharsets.UTF_8)

    /** "Farm · Cobble farm" / "Manual · Break Block" — where the material comes from. */
    val supplyPrefixed: String
        get() = when (val supply = activity.supply) {
            is SupplySource.Farm -> "Farm · ${supply.label}"
            is SupplySource.LinkedProject -> "Project · ${supply.label}"
            null -> sourceLabel ?: "Manual"
        }
}

/**
 * Builds a row's state from the plan.
 *
 * One builder for three callers — the page's first render, the progress endpoint's row swap, and
 * the Log/Stop swap. They rendered the row three slightly different ways before, which is how the
 * name lost its "(Block)" suffix the moment you pressed a counter.
 */
internal fun workRowStateOf(
    activity: Activity,
    progress: Map<String, Int>,
    nodeIngredients: Map<String, String> = emptyMap(),
    feedsLabels: Map<String, FeedsLabel> = emptyMap(),
    farmScaleIds: Set<String> = emptySet(),
): WorkRowState {
    val need = activity.quantity
    val have = (progress[activity.item.id] ?: 0).toLong().coerceIn(0, maxOf(need, 0))
    // Method plus its detail: "Smelting · 1 Raw Iron", or a pinned loot table's location.
    val detail = nodeIngredients[activity.item.id] ?: activity.source?.let { lootTableName(it) }
    val sourceLabel = listOfNotNull(activity.source?.getMethodLabel(), detail)
        .joinToString(" · ")
        .ifEmpty { null }
    return WorkRowState(
        activity = activity,
        have = have,
        isFarmScale = activity.item.id in farmScaleIds,
        sourceLabel = sourceLabel,
        feeds = feedsLabels[activity.item.id],
    )
}

/**
 * The 40px scan row: tick, name, how it is sourced, what is left, and one button.
 *
 * Everything here is either an identity or a number you read — the only control is the tick, and
 * `Log`, which asks for the row that *does* have controls. `remaining` is the loud number because
 * it is the one that answers "am I done yet"; `of 27,763` is the quiet context.
 */
internal fun FlowContent.workRowCollapsed(worldId: Int, projectId: Int, state: WorkRowState) {
    div(collapsedClasses(state)) { collapsedBody(worldId, projectId, state) }
}

/**
 * The same row as a standalone fragment, for the endpoints that swap one row rather than render
 * a page. kotlinx.html builds a nested element and a root element through different receivers, so
 * the body is shared and only the root differs.
 */
internal fun workRowCollapsedHtml(worldId: Int, projectId: Int, state: WorkRowState): String =
    createHTML().div(collapsedClasses(state)) { collapsedBody(worldId, projectId, state) }

private fun collapsedClasses(state: WorkRowState) =
    "work-row${if (state.done) " work-row--done" else ""}"

private fun DIV.collapsedBody(worldId: Int, projectId: Int, state: WorkRowState) {
    run {
        id = state.rowId
        attributes["data-item-name"] = state.activity.item.name

        workTickBox(worldId, projectId, state)

        val (itemName, itemKind) = splitKind(state.activity.item.name)
        span("work-row__name") {
            span("work-row__item") { +itemName }
            if (itemKind.isNotEmpty()) span("work-row__kind") { +itemKind }
        }

        // Demoted to a ghost chip: which method is a fact you check, not one you act on every row.
        button(classes = "btn btn--ghost btn--sm work-row__source") {
            type = ButtonType.button
            attributes["title"] = "Switch how this is sourced"
            attributes["hx-get"] = "/worlds/$worldId/projects/$projectId/plan/chain/${state.encodedItemId}"
            attributes["hx-target"] = "#project-content"
            attributes["hx-swap"] = "outerHTML"
            attributes["hx-push-url"] = "/worlds/$worldId/projects/$projectId?drill=${state.encodedItemId}"
            attributes["data-drill-nav"] = "in"
            span("work-row__swap") { +"⇆" }
            span("work-row__method") { +(if (state.isSupplied) state.supplyPrefixed else state.sourceLabel ?: "Manual") }
        }

        span("work-row__remaining") {
            span("work-row__left") { +"%,d".format(state.remaining) }
            span("work-row__of") { +"of ${"%,d".format(state.need)}" }
        }

        if (state.isFarmScale) {
            span("badge plan-farm-scale__badge work-row__farm-scale") {
                attributes["title"] = "More than this world's farm-scale threshold — worth a farm"
                +"Farm-scale"
            }
        }

        workRowAction(worldId, projectId, state, working = false)
    }
}

/**
 * The expanded strip: the same line, with the controls it needs while you are on it.
 *
 * Swapped in by `Log` rather than rendered hidden on every row — 634 hidden strips is the wall
 * this whole change exists to remove.
 */
internal fun FlowContent.workRowStrip(worldId: Int, projectId: Int, state: WorkRowState) {
    div("work-row work-row--working") { stripBody(worldId, projectId, state) }
}

/** The strip as a standalone fragment — see [workRowCollapsedHtml] on why this exists. */
internal fun workRowStripHtml(worldId: Int, projectId: Int, state: WorkRowState): String =
    createHTML().div("work-row work-row--working") { stripBody(worldId, projectId, state) }

private fun DIV.stripBody(worldId: Int, projectId: Int, state: WorkRowState) {
    run {
        id = state.rowId
        attributes["data-item-name"] = state.activity.item.name

        div("work-strip") {
            div("work-strip__head") {
                div("work-strip__identity") {
                    div("work-strip__title") {
                        val (itemName, itemKind) = splitKind(state.activity.item.name)
                        span("work-strip__name") { +itemName }
                        if (itemKind.isNotEmpty()) span("work-row__kind") { +itemKind }
                        // Labelled, never colour alone — the primary user is red-green colour-blind.
                        span("badge badge--in-progress") { +"In progress" }
                    }
                    button(classes = "btn btn--secondary btn--sm work-strip__source") {
                        type = ButtonType.button
                        attributes["title"] = "Switch how this is sourced"
                        attributes["hx-get"] =
                            "/worlds/$worldId/projects/$projectId/plan/chain/${state.encodedItemId}"
                        attributes["hx-target"] = "#project-content"
                        attributes["hx-swap"] = "outerHTML"
                        attributes["hx-push-url"] =
                            "/worlds/$worldId/projects/$projectId?drill=${state.encodedItemId}"
                        attributes["data-drill-nav"] = "in"
                        span("work-row__swap") { +"⇆" }
                        +state.supplyPrefixed
                    }
                }
                div("work-strip__figure") {
                    span("work-strip__remaining") { +"%,d".format(state.remaining) }
                    span("work-strip__unit") { +"left of ${"%,d".format(state.need)}" }
                }
            }

            div("progress work-strip__progress") {
                div("progress__fill") {
                    attributes["style"] = "width: ${state.percent}%"
                    attributes["role"] = "progressbar"
                    attributes["aria-valuenow"] = state.have.toString()
                    attributes["aria-valuemin"] = "0"
                    attributes["aria-valuemax"] = state.need.toString()
                }
            }

            div("work-strip__actions") {
                div("work-strip__logged") {
                    label("work-strip__logged-label") {
                        attributes["for"] = "logged-${state.slug}"
                        +"Logged"
                    }
                    input(classes = "work-strip__logged-input") {
                        type = InputType.text
                        id = "logged-${state.slug}"
                        attributes["inputmode"] = "numeric"
                        attributes["value"] = state.have.toString()
                        attributes["aria-label"] =
                            "Logged amount of ${state.activity.item.name}, ${state.need} needed"
                        attributes["data-item-id"] = state.activity.item.id
                        attributes["data-required"] = state.need.toString()
                        attributes["data-have"] = state.have.toString()
                        attributes["data-post"] = progressUrl(worldId, projectId)
                        attributes["data-row"] = state.rowId
                    }
                    span("work-strip__need") { +"/ ${"%,d".format(state.need)}" }

                    span("work-strip__divider")

                    span("work-strip__steps") {
                        STEPS.forEach { amount ->
                            val minus = if (amount < 0) " work-strip__step--minus" else ""
                            button(classes = "btn btn--secondary btn--sm work-strip__step$minus") {
                                type = ButtonType.button
                                stepAttributes(worldId, projectId, state, amount)
                                +(if (amount > 0) "+$amount" else "$amount")
                            }
                        }
                    }
                }

                div("work-strip__done") {
                    button(classes = "btn btn--primary btn--sm") {
                        type = ButtonType.button
                        // Marking done is "log the rest": with no stored override, done is
                        // have >= need, so completing the count *is* completing the line.
                        markDoneAttributes(worldId, projectId, state, working = true)
                        +"Mark line done"
                    }
                    button(classes = "btn btn--ghost btn--sm") {
                        type = ButtonType.button
                        workRowSwapAttributes(worldId, projectId, state, working = false)
                        +"Stop working"
                    }
                }
            }

            state.feeds?.let { feeds ->
                span("work-strip__feeds") {
                    feeds.title?.let { attributes["title"] = it }
                    +feeds.text
                }
            }
        }
    }
}

/** Log / Logging / Reopen — one button whose label says which of the three states you are in. */
private fun FlowContent.workRowAction(
    worldId: Int,
    projectId: Int,
    state: WorkRowState,
    working: Boolean,
) {
    val classes = when {
        state.done -> "btn btn--ghost btn--sm work-row__action"
        working -> "btn btn--primary btn--sm work-row__action"
        else -> "btn btn--secondary btn--sm work-row__action"
    }
    button(classes = classes) {
        type = ButtonType.button
        if (state.done) {
            // Reopening means logging less than you need; the count field in the strip is where
            // you say how much. Until a stored done-override exists, this opens the strip rather
            // than guessing a number.
            workRowSwapAttributes(worldId, projectId, state, working = true)
            +"Reopen"
        } else {
            workRowSwapAttributes(worldId, projectId, state, working = true)
            +"Log"
        }
    }
}

/**
 * The tick box. Marks the line done by logging what is left, and says so in its title rather
 * than only in colour.
 */
private fun FlowContent.workTickBox(worldId: Int, projectId: Int, state: WorkRowState) {
    button(classes = "work-row__tick${if (state.done) " work-row__tick--done" else ""}") {
        type = ButtonType.button
        attributes["aria-pressed"] = state.done.toString()
        attributes["aria-label"] =
            if (state.done) "${state.activity.item.name} is done" else "Mark ${state.activity.item.name} done"
        attributes["title"] = if (state.done) "Done" else "Mark done"
        markDoneAttributes(worldId, projectId, state, working = false)
        if (state.done) +"✓"
    }
}

private fun progressUrl(worldId: Int, projectId: Int) =
    "/worlds/$worldId/projects/$projectId/plan/progress"

/** Steppers post a delta against the same endpoint the old row used. */
private fun kotlinx.html.CommonAttributeGroupFacade.stepAttributes(
    worldId: Int,
    projectId: Int,
    state: WorkRowState,
    amount: Int,
) {
    attributes["hx-patch"] = progressUrl(worldId, projectId)
    attributes["hx-vals"] =
        """{"itemId": "${state.activity.item.id}", "amount": $amount, "required": ${state.need}, "working": true}"""
    attributes["hx-target"] = "#${state.rowId}"
    attributes["hx-swap"] = "outerHTML"
}

/** "Log the rest of it" — the delta that takes have to need. */
private fun kotlinx.html.CommonAttributeGroupFacade.markDoneAttributes(
    worldId: Int,
    projectId: Int,
    state: WorkRowState,
    working: Boolean,
) {
    attributes["hx-patch"] = progressUrl(worldId, projectId)
    attributes["hx-vals"] =
        """{"itemId": "${state.activity.item.id}", "amount": ${state.remaining}, "required": ${state.need}, "working": $working}"""
    attributes["hx-target"] = "#${state.rowId}"
    attributes["hx-swap"] = "outerHTML"
}

/** Asks the server for this row in the other form. The working set lives in the DOM. */
private fun kotlinx.html.CommonAttributeGroupFacade.workRowSwapAttributes(
    worldId: Int,
    projectId: Int,
    state: WorkRowState,
    working: Boolean,
) {
    attributes["hx-get"] =
        "/worlds/$worldId/projects/$projectId/plan/row/${state.encodedItemId}?working=$working"
    attributes["hx-target"] = "#${state.rowId}"
    attributes["hx-swap"] = "outerHTML"
}

/**
 * Splits "Oak Log (Block)" into its name and its kind.
 *
 * The suffix is part of the stored name — `ExtractionContext` appends " (Block)" / " (Item)"
 * when it builds the catalog, because `Redstone` is otherwise two different things. The design
 * sets the kind in its own quieter type, so it has to come apart again here. Anything without a
 * trailing parenthetical is left whole rather than guessed at.
 */
internal fun splitKind(name: String): Pair<String, String> {
    if (!name.endsWith(")")) return name to ""
    val open = name.lastIndexOf(" (")
    if (open <= 0) return name to ""
    return name.substring(0, open) to name.substring(open + 1)
}

/** One chip: tick, name, what is left. Ticking logs the remainder, which is what done means. */
internal fun FlowContent.smallJobChip(worldId: Int, projectId: Int, state: WorkRowState) {
    button(classes = chipClasses(state)) { chipBody(worldId, projectId, state) }
}

/** The chip as a standalone fragment — see [workRowCollapsedHtml] on why this exists. */
internal fun smallJobChipHtml(worldId: Int, projectId: Int, state: WorkRowState): String =
    createHTML().button(classes = chipClasses(state)) { chipBody(worldId, projectId, state) }

private fun chipClasses(state: WorkRowState) =
    "small-job${if (state.done) " small-job--done" else ""}"

private fun BUTTON.chipBody(worldId: Int, projectId: Int, state: WorkRowState) {
    run {
        type = ButtonType.button
        id = state.rowId
        attributes["aria-pressed"] = state.done.toString()
        attributes["aria-label"] =
            if (state.done) "${state.activity.item.name} is done"
            else "Mark ${state.activity.item.name} done, ${state.remaining} left"
        attributes["hx-patch"] = "/worlds/$worldId/projects/$projectId/plan/progress"
        attributes["hx-vals"] =
            """{"itemId": "${state.activity.item.id}", "amount": ${if (state.done) -state.need else state.remaining}, "required": ${state.need}, "chip": true}"""
        attributes["hx-target"] = "#${state.rowId}"
        attributes["hx-swap"] = "outerHTML"

        span("small-job__tick") { if (state.done) +"✓" }
        span("small-job__name") { +splitKind(state.activity.item.name).first }
        span("small-job__count") { +"%,d".format(state.remaining) }
    }
}
