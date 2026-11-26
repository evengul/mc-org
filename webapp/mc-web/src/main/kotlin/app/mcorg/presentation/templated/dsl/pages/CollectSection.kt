package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.engine.plan.Activity
import app.mcorg.pipeline.resources.CollectBlocks
import app.mcorg.pipeline.resources.FarmRun
import app.mcorg.pipeline.resources.ItemRun
import app.mcorg.pipeline.resources.ModeRun
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.dsl.Link
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.summary

/**
 * The plan's Collect section, one block per farm (MCO-604, design frame 2a).
 *
 * [CollectBlocks] decides the order and the folds; this draws them. Inside a block the lines keep
 * the section-wide split into rows and chips ([ActivitySectionLayout]), with one exception: the
 * line a farm's time comes from is always a row, because it is the line that decides when the
 * player is done there. 455 Bow is a row although it is a small job by quantity.
 *
 * Lines no farm run covers (a linked project's supply, or a farm the runs could not be loaded for)
 * are drawn after the blocks the way the section drew every line before.
 */
internal fun FlowContent.collectSection(
    worldId: Int,
    projectId: Int,
    activities: List<Activity>,
    runs: List<FarmRun>,
    leadIds: Set<String>,
    /** Whether any line has a chest count, which reserves the rows' chests column (see `workSection`). */
    hasChests: Boolean,
    stateOf: (Activity) -> WorkRowState,
    rest: FlowContent.(List<Activity>) -> Unit,
) {
    val byItem = activities.associateBy { it.item.id }
    val covered = runs.flatMap { run -> run.items.map { it.line.itemId } }.toSet()
    val blocks = CollectBlocks.of(runs)

    div("collect__head") {
        span("section-label") { +"Collect · longest run first" }
        span("collect__note") {
            +"Times are estimates from each design's measured rate. Supply isn't capped; collect whenever."
        }
    }

    // A rate changed in a farm's Produces panel ("Add a rate", opened from this section) changes
    // the times and the order here, but the panel belongs to the farm and cannot know which plan is
    // open behind it. Its endpoints announce the change (`productionsChanged`), and the plan fetches
    // itself again out of band: the panel stays open over a plan that already reads the new rate.
    div("collect__refresh") {
        attributes["hidden"] = ""
        attributes["hx-get"] = "/worlds/$worldId/projects/$projectId/detail-content?lens=list&oob=true"
        attributes["hx-trigger"] = "productionsChanged from:body"
        attributes["hx-swap"] = "none"
    }

    div("work-panel collect-panel" + if (hasChests) " work-panel--chests" else "") {
        blocks.shown.forEach { collectBlock(worldId, projectId, it, byItem, leadIds, stateOf) }

        if (blocks.folded.isNotEmpty()) {
            details("collect-fold") {
                summary("collect-fold__summary") {
                    span("collect-fold__count") {
                        +"${blocks.folded.size} more ${if (blocks.folded.size == 1) "farm" else "farms"}, under 30 min each"
                    }
                    span("collect-fold__list") { +foldedList(blocks.folded) }
                    showHide()
                }
                blocks.folded.forEach { collectBlock(worldId, projectId, it, byItem, leadIds, stateOf) }
            }
        }

        if (blocks.done.isNotEmpty()) {
            div("collect-done") {
                span("collect-done__label") {
                    +"Done · ${blocks.done.size} ${if (blocks.done.size == 1) "farm" else "farms"}"
                }
                blocks.done.forEach { run ->
                    details("collect-done__farm") {
                        summary("collect-done__summary") {
                            span("collect-done__tick") { +"✓" }
                            span("collect-done__name") { +run.projectName }
                            span("collect-done__items") {
                                +"Done · "
                                +run.items.joinToString(", ") { "${splitKind(it.line.itemName).first} ${"%,d".format(it.line.quantity)}" }
                            }
                            showHide()
                        }
                        collectBlockBody(worldId, projectId, run, byItem, leadIds, stateOf)
                    }
                }
            }
        }
    }

    val uncovered = activities.filterNot { it.item.id in covered }
    if (uncovered.isNotEmpty()) rest(uncovered)
}

/**
 * What a folded farm list says: the farms that take a few minutes by name and time, and the ones
 * that take a minute or less together, since a time on each of those says nothing.
 */
private fun foldedList(folded: List<FarmRun>): String {
    val (seconds, minutes) = folded.partition { runningTime(it.hoursLeft!!) == "~1 min" }
    val timed = minutes.map { "${it.projectName} ${runningTime(it.hoursLeft!!)}" }
    val quick = seconds.takeIf { it.isNotEmpty() }?.let { quick ->
        quick.joinToString(", ") { it.projectName } + if (quick.size == 1) ", about a minute or less" else ", about a minute or less each"
    }
    return (timed + listOfNotNull(quick)).joinToString(" · ")
}

private fun FlowContent.collectBlock(
    worldId: Int,
    projectId: Int,
    run: FarmRun,
    byItem: Map<String, Activity>,
    leadIds: Set<String>,
    stateOf: (Activity) -> WorkRowState,
) {
    div("collect-block") {
        id = farmBlockId(run.projectId)
        div("collect-block__head") {
            id = farmHeadId(run.projectId)
            farmHead(worldId, run)
        }
        collectBlockBody(worldId, projectId, run, byItem, leadIds, stateOf)
    }
}

/**
 * A block's lines. A farm with modes draws a heading per mode that takes real time, and the modes
 * that run under a minute together under one; a farm without modes is one run and needs neither.
 */
private fun FlowContent.collectBlockBody(
    worldId: Int,
    projectId: Int,
    run: FarmRun,
    byItem: Map<String, Activity>,
    leadIds: Set<String>,
    stateOf: (Activity) -> WorkRowState,
) {
    fun state(item: ItemRun): WorkRowState? =
        byItem[item.line.itemId]?.let { stateOf(it).copy(farmRole = run.roleOf(item.line.itemId)) }

    if (run.farmModeCount == 0) {
        modeLines(worldId, projectId, run.modes.flatMap { it.items }, run.modes.singleOrNull(), leadIds, ::state)
        return
    }

    val (tokens, timed) = run.modes.partition { it.isToken }
    timed.forEach { mode ->
        div("collect-block__mode") {
            div("collect-block__mode-head") {
                id = modeHeadId(run.projectId, mode)
                modeHead(mode)
            }
            modeLines(worldId, projectId, mode.items, mode, leadIds, ::state)
        }
    }
    if (tokens.isNotEmpty()) {
        div("collect-block__tokens") {
            span("collect-block__mode-name") {
                +when {
                    tokens.size == 1 -> "${tokens.single().modeName} · under a minute"
                    timed.isEmpty() -> "${tokens.size} modes · under a minute each"
                    else -> "${tokens.size} more modes · under a minute each"
                }
            }
            div("small-jobs__chips") {
                tokens.flatMap { it.items }.mapNotNull(::state).forEach { smallJobChip(worldId, projectId, it) }
            }
        }
    }
}

/**
 * The toggle on a fold's summary: "Show" while it is shut, "Hide" while it is open. Both are drawn
 * and the CSS picks one from the details element's `open`, so no script keeps them in step.
 */
private fun FlowContent.showHide() {
    span("btn btn--ghost btn--sm collect-fold__toggle") {
        span("collect-fold__show") { +"Show ▾" }
        span("collect-fold__hide") { +"Hide ▴" }
    }
}

/** One run's lines: rows for the lines worth one and the line that sets the time, chips for the rest. */
private fun FlowContent.modeLines(
    worldId: Int,
    projectId: Int,
    items: List<ItemRun>,
    mode: ModeRun?,
    leadIds: Set<String>,
    state: (ItemRun) -> WorkRowState?,
) {
    val ordered = items.sortedByDescending { it.line.quantity }
    // The line the time comes from is a row; with no time (a rate of 0), the biggest line is,
    // so a block never opens on chips alone.
    val anchor = mode?.setBy?.line?.itemId ?: ordered.firstOrNull()?.line?.itemId
    val (rows, chips) = ordered.partition { it.line.itemId in leadIds || it.line.itemId == anchor }
    rows.mapNotNull(state).forEach { workRowCollapsed(worldId, projectId, it) }
    val chipStates = chips.mapNotNull(state)
    if (chipStates.isNotEmpty()) {
        // Folded on a phone, where each chip is a 44px row and eight of them bury the next farm
        // (frame 2a·D: "+7 small jobs · Show"). On a wider screen the CSS hides the summary and
        // shows the chips whether the fold is open or not; see collect-block.css.
        details("collect-block__small") {
            summary("collect-block__small-summary") {
                span("collect-block__small-count") {
                    +"+${chipStates.size} small ${if (chipStates.size == 1) "job" else "jobs"}"
                }
                showHide()
            }
            div("collect-block__chips small-jobs__chips") {
                chipStates.take(BLOCK_CHIPS).forEach { smallJobChip(worldId, projectId, it) }
                if (chipStates.size > BLOCK_CHIPS) {
                    details("small-jobs__rest") {
                        summary { span("btn btn--ghost btn--sm") { +"Show all ${chipStates.size}" } }
                        div("small-jobs__chips") {
                            chipStates.drop(BLOCK_CHIPS).forEach { smallJobChip(worldId, projectId, it) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Chips a block shows before "Show all". An End Trading Hall makes forty kinds of glazed
 * terracotta, one each; a wall of them under one heading buries the next farm.
 */
private const val BLOCK_CHIPS = 8

/** The farm run an item's line belongs to, if a farm supplies it. */
internal fun List<FarmRun>.runFor(itemId: String): FarmRun? =
    firstOrNull { run -> run.items.any { it.line.itemId == itemId } }

/**
 * Where an item's line sits in its farm's block: the mode a chip names when it shares the "under a
 * minute" heading. The same answer for the first render and for the swap after a line is logged,
 * which is why the progress endpoint calls it too.
 */
internal fun FarmRun.roleOf(itemId: String): FarmLineRole {
    val mode = modes.firstOrNull { m -> m.items.any { it.line.itemId == itemId } } ?: return FarmLineRole()
    // One mode under a minute has the heading to itself ("Oak Mode · under a minute"), so its chip
    // need not say it again; several share "5 more modes", and then each chip names its own.
    val sharesHeading = modes.count { it.isToken } > 1
    return FarmLineRole(
        modeLabel = if (mode.isToken && farmModeCount > 0 && sharesHeading) mode.modeName else null,
    )
}

/** "~13 min left" while a line is partly logged, else just the time. */
private fun DIV.timeLeft(hoursLeft: Double, hoursNeeded: Double?, inProgress: Boolean) {
    if (inProgress && hoursNeeded != null) {
        span("collect-block__time") { +"${runningTime(hoursLeft)} left" }
        span("collect-block__of") { +"of ${runningTime(hoursNeeded)}" }
    } else {
        span("collect-block__time") { +runningTime(hoursLeft) }
    }
}

/**
 * The block heading: farm, time, what the time comes from, and the way to the farm.
 *
 * A long time is in ink like any other. It is not a warning: a bartering setup that runs twelve
 * days is a farm you leave running, not a problem.
 */
private fun DIV.farmHead(worldId: Int, run: FarmRun) {
    span("collect-block__name") { +run.projectName }
    val hoursLeft = run.hoursLeft
    if (run.isDone) {
        // Before the unknown-time branch: a farm with every line logged has no time either, and it
        // is finished, not unmeasured. The block moves under "Done" on the next render (frame 2a·F).
        span("collect-block__time") { +"Done" }
    } else if (hoursLeft != null) {
        timeLeft(hoursLeft, run.hoursNeeded, run.isInProgress)
        span("collect-block__detail") {
            if (run.farmModeCount > 0) {
                +"${run.modes.size} of its ${run.farmModeCount} modes for this plan"
            } else {
                run.setBy?.let { +setByText(it) }
            }
        }
    } else {
        span("collect-block__time") { +"Unknown time" }
        span("collect-block__detail") { +unmeasuredText(run.unmeasured) }
        button(classes = "btn btn--ghost btn--sm collect-block__add-rate") {
            type = ButtonType.button
            attributes["data-production-panel-url"] = "${productionsBase(worldId, run.projectId)}/panel"
            +"Add a rate"
        }
    }
    a(classes = "collect-block__farm-link") {
        href = Link.Worlds.world(worldId).project(run.projectId).to
        +"Farm page →"
    }
}

private fun DIV.modeHead(mode: ModeRun) {
    span("collect-block__mode-name") { +(mode.modeName ?: "") }
    val hoursLeft = mode.hoursLeft
    val setBy = mode.setBy
    if (mode.isDone) {
        span("collect-block__time") { +"Done" }
        return
    }
    if (hoursLeft == null || setBy == null) {
        span("collect-block__time") { +"Unknown time" }
        return
    }
    val inProgress = mode.items.any { it.line.isInProgress }
    timeLeft(hoursLeft, mode.hoursNeeded, inProgress)
    span("collect-block__detail") {
        +if (inProgress) {
            "· ${"%,d".format(setBy.line.left)} to go at ${"%,d".format(setBy.ratePerHour)}/hr"
        } else {
            "${"%,d".format(setBy.ratePerHour)}/hr"
        }
    }
}

/** "set by Iron Nugget, 296,800 at 1,000/hr" — what is left, since that is what the time is from. */
private fun setByText(setBy: ItemRun): String {
    val name = splitKind(setBy.line.itemName).first
    val amount = "%,d".format(setBy.line.left)
    val rate = "%,d".format(setBy.ratePerHour)
    return if (setBy.line.logged > 0) "set by $name, $amount to go at $rate/hr" else "set by $name, $amount at $rate/hr"
}

/** "Honeycomb rate is 0", or the first of several and how many more. */
private fun unmeasuredText(unmeasured: List<ItemRun>): String {
    val first = unmeasured.firstOrNull()?.let { splitKind(it.line.itemName).first } ?: return "· rate is 0"
    return when (unmeasured.size) {
        1 -> "· $first rate is 0"
        else -> "· $first and ${unmeasured.size - 1} more: rates are 0"
    }
}

private fun farmBlockId(farmId: Int) = "farm-run-$farmId"
private fun farmHeadId(farmId: Int) = "farm-run-$farmId-head"
private fun modeHeadId(farmId: Int, mode: ModeRun) = "farm-run-$farmId-mode-${mode.modeId ?: 0}"

/**
 * The headings of the farm a logged line belongs to, swapped out of band beside the row.
 *
 * Only the headings: swapping the whole block would close any other line of it that is open in a
 * work strip, and the order of the blocks is left until the next render (a block with a line in
 * progress keeps its place anyway).
 */
internal fun farmHeadingsOobHtml(worldId: Int, run: FarmRun): String = buildString {
    append(
        createHTML().div("collect-block__head") {
            id = farmHeadId(run.projectId)
            hxOutOfBands("true")
            farmHead(worldId, run)
        }
    )
    if (run.farmModeCount > 0) {
        run.modes.filterNot { it.isToken }.forEach { mode ->
            append(
                createHTML().div("collect-block__mode-head") {
                    id = modeHeadId(run.projectId, mode)
                    hxOutOfBands("true")
                    modeHead(mode)
                }
            )
        }
    }
}
