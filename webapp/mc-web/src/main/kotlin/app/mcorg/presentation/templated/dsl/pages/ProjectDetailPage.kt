package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.domain.model.task.ActionTask
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.engine.plan.Activity
import app.mcorg.engine.plan.ActivityGroup
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTargetError
import app.mcorg.presentation.hxTrigger
import app.mcorg.presentation.templated.dsl.TabItem
import app.mcorg.presentation.templated.dsl.TabVariant
import app.mcorg.presentation.templated.dsl.addTaskInline
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.pageShell
import app.mcorg.presentation.templated.dsl.progressBar
import app.mcorg.presentation.templated.dsl.resourceRow
import app.mcorg.presentation.templated.dsl.projectLocationField
import app.mcorg.presentation.templated.dsl.projectNameField
import app.mcorg.presentation.templated.dsl.projectStateField
import app.mcorg.presentation.templated.dsl.resourceSearch
import app.mcorg.presentation.templated.dsl.tabStrip
import app.mcorg.presentation.templated.dsl.taskList
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import app.mcorg.engine.plan.TargetTree
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun projectDetailPage(
    user: TokenProfile,
    project: Project,
    worldName: String,
    resources: List<ResourceGatheringItem>,
    tasks: List<ActionTask>,
    lens: String = "list",
    isWorldAdmin: Boolean = false,
    plan: GatheringPlan? = null,
    progressMap: Map<String, Int> = emptyMap(),
    drillTarget: TargetTree? = null,
    drillCandidateCounts: Map<String, Int> = emptyMap(),
): String = pageShell(
    pageTitle = "Seam — ${project.name}",
    user = user,
    stylesheets = listOf(
        "/static/styles/components/btn.css",
        "/static/styles/components/form.css",
        "/static/styles/components/item-search.css",
        "/static/styles/components/badge.css",
        "/static/styles/components/progress.css",
        "/static/styles/components/resource-row.css",
        "/static/styles/components/task-list.css",
        "/static/styles/components/resource-search.css",
        "/static/styles/components/resource-panel.css",
        "/static/styles/components/callout.css",
        "/static/styles/components/drill.css",
        "/static/styles/pages/project-detail.css",
    ),
    scripts = listOf(
        "/static/scripts/resource-search.js",
        "/static/scripts/plan-view.js",
        "/static/scripts/resource-panel.js"
    )
) {
    appHeader(
        worldId = project.worldId,
        projectId = project.id,
        user = user,
        isWorldAdmin = isWorldAdmin,
        breadcrumbBlock = {
            link("Worlds", "/worlds")
                .link(worldName, "/worlds/${project.worldId}/projects")
                .current(project.name)
        }
    )
    // Mobile header
    div("project-detail__mobile-header") {
        a(classes = "project-detail__back-btn") {
            href = "/worlds/${project.worldId}/projects"
            +"←"
        }
        p("project-detail__mobile-name") { +project.name }
    }
    main {
        container {
            // Desktop header
            div("project-detail__header") {
                div("project-detail__header-left") {
                    projectNameField(project, isWorldAdmin)
                    div("project-detail__meta") {
                        projectStateField(project, isWorldAdmin)
                        projectLocationField(project, isWorldAdmin)
                    }
                    gatheringOverallProgress(project.id, project.worldId, resources, plan, progressMap)
                }
            }

            div {
                id = "project-content"
                if (drillTarget != null) {
                    // ?drill=<item> deep-links straight into a target's chain (reload/share-safe).
                    drillChainContent(project, drillTarget, drillCandidateCounts)
                } else {
                    gatheringPlannerContent(project, resources, tasks, plan, lens, progressMap)
                }
            }
        }
    }
    dialog {
        id = "resource-panel"
        div {
            id = "resource-panel-content"
        }
    }
}

/**
 * Overall gathering progress bar shown in the page header.
 * When a plan is available, totals come from countable activities (RESOLVED/RAW_GATHER).
 * Collected values come from [progressMap] (all persisted progress for the project),
 * which covers both defined targets and engine-derived activities.
 */
private fun FlowContent.gatheringOverallProgress(
    projectId: Int,
    worldId: Int,
    resources: List<ResourceGatheringItem>,
    plan: GatheringPlan?,
    progressMap: Map<String, Int> = emptyMap(),
) {
    val (totalRequired, totalCollected) = if (plan != null) {
        planProgressTotals(plan, progressMap)
    } else {
        val filtered = resources.filter { it.required > 0 }
        filtered.sumOf { it.required }.toLong() to filtered.sumOf { it.collected }.toLong()
    }

    if (totalRequired > 0) {
        div("project-detail__overall-progress") {
            // Label lives INSIDE #overall-progress so the OOB swap after a counter update
            // refreshes both the label and the bar together (it previously left a stale label).
            div {
                id = "overall-progress"
                overallProgressInner(totalRequired, totalCollected)
            }
        }
    }
}

/** Inner content of #overall-progress: the "N% gathered · M to go" label + the bar. */
fun FlowContent.overallProgressInner(totalRequired: Long, totalCollected: Long) {
    val pct = if (totalRequired > 0) (totalCollected * 100 / totalRequired) else 0
    val toGo = (totalRequired - totalCollected).coerceAtLeast(0)
    p("project-detail__overall-progress-label") {
        +"$pct% gathered · $toGo to go"
    }
    progressBar(totalCollected.toInt().coerceAtMost(totalRequired.toInt()), totalRequired.toInt())
}

/**
 * Computes (totalRequired, totalCollected) from countable plan activities.
 * Only RESOLVED and RAW_GATHER activities contribute to the progress counter.
 * Collected is sourced from [progressMap] (resource_gathering_progress for the whole project),
 * so derived activities that have persisted progress are counted correctly.
 */
internal fun planProgressTotals(plan: GatheringPlan, progressMap: Map<String, Int>): Pair<Long, Long> {
    val countable = plan.activityList.filter {
        it.status == PlanNodeStatus.RESOLVED || it.status == PlanNodeStatus.RAW_GATHER
    }
    val totalRequired = countable.sumOf { it.quantity }
    val totalCollected = countable.sumOf { activity ->
        (progressMap[activity.item.id] ?: 0).toLong()
    }
    return totalRequired to totalCollected
}

/**
 * Unified gathering planner content — replaces the old PLAN/EXECUTE toggle.
 * Shows lens pills (List / Next up / Sessions) and renders the active lens body.
 */
fun FlowContent.gatheringPlannerContent(
    project: Project,
    resources: List<ResourceGatheringItem>,
    tasks: List<ActionTask>,
    plan: GatheringPlan?,
    lens: String = "list",
    progressMap: Map<String, Int> = emptyMap(),
) {
    val activeLens = when (lens) {
        "next", "sessions" -> lens
        else -> "list"
    }

    // Fetch from the fragment endpoint (hx-get), but push the canonical page URL so a
    // reload/share lands on the full page shell rather than the bare CSS-less fragment.
    val fragmentBase = "/worlds/${project.worldId}/projects/${project.id}/detail-content"
    val pageBase = "/worlds/${project.worldId}/projects/${project.id}"
    val lensTabs = listOf(
        TabItem("list", "List", "$fragmentBase?lens=list", pushUrl = "$pageBase?lens=list"),
        TabItem("next", "Next up", "$fragmentBase?lens=next", pushUrl = "$pageBase?lens=next"),
        TabItem("sessions", "Sessions", "$fragmentBase?lens=sessions", pushUrl = "$pageBase?lens=sessions"),
    )

    // Lens pills
    tabStrip(
        tabs = lensTabs,
        activeValue = activeLens,
        hxTarget = "#project-content",
        variant = TabVariant.PILLS,
        queryName = "lens",
    )

    // Active lens body
    when (activeLens) {
        "next", "sessions" -> lensComingSoon(project.worldId, project.id, activeLens)
        else -> listLensContent(project, resources, tasks, plan, progressMap)
    }
}

private fun FlowContent.lensComingSoon(worldId: Int, projectId: Int, lens: String) {
    val label = if (lens == "next") "Next up" else "Sessions"
    div("callout callout--info") {
        id = "lens-content"
        span("callout__icon") { +"i" }
        div("callout__body") {
            +"$label view is coming soon."
        }
    }
}

/**
 * The List lens: definition controls + grouped activity list (or empty/error state).
 */
private fun FlowContent.listLensContent(
    project: Project,
    resources: List<ResourceGatheringItem>,
    tasks: List<ActionTask>,
    plan: GatheringPlan?,
    progressMap: Map<String, Int> = emptyMap(),
) {
    // Resources / definition section
    div("project-detail__section") {
        div("project-detail__section-header") {
            span("project-detail__section-title section-label") { +"Resources" }
            div("project-detail__section-actions") {
                button(classes = "btn btn--ghost btn--sm") {
                    id = "plan-upload-schematic-btn"
                    type = ButtonType.button
                    attributes["onclick"] =
                        "document.getElementById('resource-schematic-modal')?.showModal()"
                    +"Upload schematic"
                }
                button(classes = "btn btn--secondary btn--sm plan-add-resource-btn") {
                    id = "plan-add-resource-btn"
                    type = ButtonType.button
                    +"+ Add resource"
                }
            }
        }

        // Add resource form (hidden by default via JS)
        div("plan-add-resource-form") {
            id = "plan-add-resource-form"
            form {
                id = "plan-resource-form"
                div("plan-add-resource-form__fields") {
                    div("plan-add-resource-form__field plan-add-resource-form__field--item") {
                        label("plan-add-resource-form__label") {
                            htmlFor = "plan-item-search"
                            +"Item"
                        }
                        div("item-search-field") {
                            input(type = InputType.text, classes = "form-control") {
                                id = "plan-item-search"
                                placeholder = "Search items by name..."
                                autoComplete = "off"
                                hxGet("/items/search")
                                hxTrigger("input changed delay:300ms")
                                hxTarget("#plan-item-search-results")
                                hxSwap("innerHTML")
                                attributes["hx-vals"] = "js:{q: this.value}"
                            }
                            div("item-search-results") {
                                id = "plan-item-search-results"
                            }
                        }
                        hiddenInput {
                            id = "plan-selected-item-id"
                            name = "requiredItemId"
                        }
                    }
                    div("plan-add-resource-form__field plan-add-resource-form__field--qty") {
                        label("plan-add-resource-form__label") {
                            htmlFor = "plan-item-amount"
                            +"Quantity"
                        }
                        input(type = InputType.number, classes = "form-control") {
                            id = "plan-item-amount"
                            name = "requiredAmount"
                            min = "1"
                            max = "2000000000"
                            value = "1"
                        }
                    }
                    div("plan-add-resource-form__actions") {
                        button(classes = "btn btn--primary btn--sm") {
                            id = "plan-add-resource-submit"
                            type = ButtonType.button
                            +"Add"
                        }
                        button(classes = "btn btn--ghost btn--sm") {
                            id = "plan-add-resource-cancel"
                            type = ButtonType.button
                            +"Cancel"
                        }
                    }
                }
            }
        }

        // Resource table — always rendered as HTMX swap target
        planResourceTable(project.worldId, project.id, resources)

        // Schematic upload modal
        resourceSchematicModal(project.worldId, project.id, resources.filter { it.required > 0 }.size)
    }

    // Gathering plan activity sections
    gatheringPlanSections(project, plan, progressMap)

    // Tasks section (collapsed)
    div("project-detail__section") {
        div("project-detail__section-header plan-tasks-header") {
            id = "plan-tasks-header"
            span("project-detail__section-title section-label") {
                val done = tasks.count { it.completed }
                +"Tasks — $done / ${tasks.size}"
            }
            button(classes = "btn btn--ghost btn--sm plan-tasks-toggle") {
                id = "plan-tasks-toggle"
                type = ButtonType.button
                +"Show"
            }
        }
        div("task-section tasks-section--collapsed") {
            id = "plan-task-section"
            div {
                id = "project-progress"
                val done = tasks.count { it.completed }
                if (tasks.isNotEmpty()) {
                    progressBar(done, tasks.size)
                    p("project-detail__overall-progress-label") {
                        +"$done of ${tasks.size} tasks completed"
                    }
                }
            }
            taskList(project.worldId, project.id, tasks)
            addTaskInline(project.worldId, project.id)
        }
    }
}

/**
 * Renders the grouped activity list from the plan, or a fallback when the plan
 * could not be derived.
 *
 * - null plan (nothing defined yet, all collected, or no ingested graph):
 *   renders the empty/definition state.
 * - plan provided: renders sections grouped by ActivityGroup.
 */
fun FlowContent.gatheringPlanSections(
    project: Project,
    plan: GatheringPlan?,
    progressMap: Map<String, Int> = emptyMap(),
) {
    if (plan == null) {
        // Empty state — no resources yet or all collected
        div("plan-empty-state") {
            id = "plan-empty-state"
            p("plan-empty-state__text") { +"No gathering plan yet." }
            p("plan-empty-state__hint") { +"Add resources above to start planning." }
        }
        return
    }

    val byGroup = plan.activityList.groupBy { it.group }
    val groupOrder = ActivityGroup.values()

    div {
        id = "gathering-plan-sections"
        groupOrder.forEach { group ->
            val activities = byGroup[group] ?: return@forEach
            if (activities.isEmpty()) return@forEach

            div("project-detail__section") {
                span("section-label") { +groupLabel(group) }
                div("resource-list") {
                    activities.forEach { activity ->
                        planActivityRow(project.worldId, project.id, activity, progressMap)
                    }
                }
            }
        }
    }
}

/** Renders a single activity row. Presentation depends on status. */
private fun FlowContent.planActivityRow(
    worldId: Int,
    projectId: Int,
    activity: Activity,
    progressMap: Map<String, Int> = emptyMap(),
) {
    when (activity.status) {
        PlanNodeStatus.SUPPLIED -> suppliedActivityRow(worldId, projectId, activity)
        PlanNodeStatus.OPEN_TAG -> openTagActivityRow(worldId, projectId, activity)
        PlanNodeStatus.BLOCKED -> blockedActivityRow(worldId, projectId, activity)
        PlanNodeStatus.RESOLVED, PlanNodeStatus.RAW_GATHER ->
            counterActivityRow(worldId, projectId, activity, progressMap)
    }
}

/** SUPPLIED row: badge + supply label, no counter. */
private fun FlowContent.suppliedActivityRow(worldId: Int, projectId: Int, activity: Activity) {
    val supplyLabel = activity.supply?.label ?: "Supplied"
    val encodedItemId = URLEncoder.encode(activity.item.id, StandardCharsets.UTF_8)
    div("resource-row") {
        id = "plan-activity-${activity.item.id.replace(":", "-")}"
        div("resource-row__desktop") {
            div("resource-row__name") { +activity.item.name }
            span("badge badge--accent") { +"Supplied" }
            span("resource-row__source") { +"from $supplyLabel" }
            drillButton(worldId, projectId, encodedItemId)
        }
    }
}

/** OPEN_TAG row: amber callout, indicates variant pick needed. */
private fun FlowContent.openTagActivityRow(worldId: Int, projectId: Int, activity: Activity) {
    val encodedItemId = URLEncoder.encode(activity.item.id, StandardCharsets.UTF_8)
    val pickerSlotId = "picker-${activity.item.id.replace(Regex("[^a-zA-Z0-9]"), "-")}"
    div("callout callout--warning") {
        id = "plan-activity-${activity.item.id.replace(":", "-")}"
        span("callout__icon") { +"!" }
        div("callout__body") {
            span { +activity.item.name }
            +" — Pick a variant (open tag)"
        }
        // Resolve inline: drops the tag-member picker below this row; a pick re-renders the
        // List lens (origin=list) so the resolved tag leaves "Needs attention".
        button(classes = "btn btn--primary btn--sm") {
            type = ButtonType.button
            attributes["hx-get"] =
                "/worlds/$worldId/projects/$projectId/plan/chain/$encodedItemId/sources?node=$encodedItemId&origin=list"
            attributes["hx-target"] = "#$pickerSlotId"
            attributes["hx-swap"] = "innerHTML"
            +"Pick variant"
        }
        // ⇄ still opens the full drill to explore/re-pin the whole chain.
        drillButton(worldId, projectId, encodedItemId)
    }
    div("chain-node__picker") { id = pickerSlotId }
}

/** BLOCKED row: warning callout. */
private fun FlowContent.blockedActivityRow(worldId: Int, projectId: Int, activity: Activity) {
    val encodedItemId = URLEncoder.encode(activity.item.id, StandardCharsets.UTF_8)
    div("callout callout--warning") {
        id = "plan-activity-${activity.item.id.replace(":", "-")}"
        span("callout__icon") { +"!" }
        div("callout__body") {
            span { +"Blocked: " }
            +activity.item.name
            +" — no feasible source found"
        }
        drillButton(worldId, projectId, encodedItemId)
    }
}

/**
 * RESOLVED / RAW_GATHER: counter row posting to the (projectId, itemId) progress endpoint.
 * Mirrors the structure of resourceRow but targets the plan progress endpoint.
 * [progressMap] carries persisted progress for all items in the project (including derived ones).
 */
fun FlowContent.counterActivityRow(
    worldId: Int,
    projectId: Int,
    activity: Activity,
    progressMap: Map<String, Int> = emptyMap(),
) {
    val itemSlug = activity.item.id.replace(":", "-")
    val rowId = "plan-activity-$itemSlug"
    val required = activity.quantity
    val current = (progressMap[activity.item.id] ?: 0).toLong().coerceIn(0, required)
    val percent = if (required > 0) (current * 100 / required).toInt() else 0
    val sourceLabel = activity.source?.getMethodLabel()
    val encodedItemId = URLEncoder.encode(activity.item.id, StandardCharsets.UTF_8)

    div("resource-row") {
        id = rowId
        attributes["data-item-name"] = activity.item.name
        attributes["data-progress-pct"] = percent.toString()
        attributes["data-required"] = required.toString()

        div("resource-row__desktop") {
            div("resource-row__name") { +activity.item.name }

            div("resource-row__progress") {
                div("progress") {
                    div("progress__fill") {
                        attributes["style"] = "width: ${percent}%"
                        attributes["role"] = "progressbar"
                        attributes["aria-valuenow"] = current.toString()
                        attributes["aria-valuemin"] = "0"
                        attributes["aria-valuemax"] = required.toString()
                    }
                }
            }

            planActivityCount(activity.item.id, activity.item.name, itemSlug, current, required, complete = false)

            if (sourceLabel != null) {
                span("resource-row__source") { +sourceLabel }
            }

            drillButton(worldId, projectId, encodedItemId)

            div("resource-row__counters") {
                intArrayOf(-1728, -64, -1, 1, 64, 1728).forEach { amount ->
                    button(classes = "btn btn--ghost btn--sm resource-row__counter-btn") {
                        attributes["hx-patch"] =
                            "/worlds/$worldId/projects/$projectId/plan/progress"
                        attributes["hx-vals"] =
                            """{"itemId": "${activity.item.id}", "amount": $amount, "required": $required}"""
                        attributes["hx-target"] = "#$rowId"
                        attributes["hx-swap"] = "outerHTML"
                        +if (amount > 0) "+$amount" else "$amount"
                    }
                }
            }
        }
    }
}

/**
 * The ⇄ drill button that navigates to the chain drill view for an activity's item.
 * [encodedItemId] must be URL-encoded (e.g. `%23minecraft:planks` for tag ids with `#`).
 */
private fun FlowContent.drillButton(worldId: Int, projectId: Int, encodedItemId: String) {
    val drillUrl = "/worlds/$worldId/projects/$projectId/plan/chain/$encodedItemId"
    val pushUrl = "/worlds/$worldId/projects/$projectId?drill=$encodedItemId"
    button(classes = "btn btn--ghost btn--sm") {
        type = ButtonType.button
        attributes["hx-get"] = drillUrl
        attributes["hx-target"] = "#project-content"
        attributes["hx-swap"] = "outerHTML"
        attributes["hx-push-url"] = pushUrl
        attributes["aria-label"] = "View source chain"
        +"⇄"
    }
}

/**
 * The "collected / required" count for a plan activity. The left number is click-to-edit
 * (keyboard-activatable): clicking reveals an input that sets an absolute collected value,
 * persisted via the same `/plan/progress` endpoint (plan-view.js computes the delta).
 * Shared by the initial render and the post-update OOB-less row swap so they stay identical.
 */
fun FlowContent.planActivityCount(
    itemId: String,
    itemName: String,
    itemSlug: String,
    current: Long,
    required: Long,
    complete: Boolean,
) {
    span("resource-row__count${if (complete) " resource-row__count--complete" else ""}") {
        id = "plan-count-$itemSlug"
        attributes["data-item-id"] = itemId
        attributes["data-current"] = current.toString()
        attributes["data-required"] = required.toString()
        span("resource-row__count-current") {
            attributes["role"] = "button"
            attributes["tabindex"] = "0"
            attributes["title"] = "Click to set collected amount"
            attributes["aria-label"] = "Set collected amount for $itemName"
            +current.toString()
        }
        span("resource-row__count-sep") { +" / $required" }
        input(type = InputType.number, classes = "resource-row__count-input") {
            attributes["aria-label"] = "Collected amount for $itemName"
            value = current.toString()
            min = "0"
            max = required.toString()
        }
    }
}

private fun groupLabel(group: ActivityGroup): String = when (group) {
    ActivityGroup.NEEDS_ATTENTION -> "Needs attention"
    ActivityGroup.COLLECT_SUPPLIED -> "Collect from farms"
    ActivityGroup.GATHER -> "Gather"
    ActivityGroup.HUNT -> "Hunt"
    ActivityGroup.LOOT -> "Loot"
    ActivityGroup.TRADE -> "Trade"
    ActivityGroup.SMELT -> "Smelt"
    ActivityGroup.CRAFT -> "Craft"
    ActivityGroup.OTHER -> "Other"
}

fun TR.planResourceRow(worldId: Int, projectId: Int, item: ResourceGatheringItem) {
    id = "plan-row-${item.id}"
    attributes["data-resource-id"] = item.id.toString()

    val dotModifier = if (item.sourceType != null) "status-dot--set" else "status-dot--unset"
    td("plan-resource-table__status") {
        span("status-dot $dotModifier") {}
    }
    td("plan-resource-table__item") {
        +item.name
    }
    td("plan-resource-table__qty") {
        attributes["data-resource-id"] = item.id.toString()
        attributes["data-current-qty"] = item.required.toString()
        span("plan-resource-table__qty-display") {
            +item.required.toString()
        }
        input(type = InputType.number, classes = "plan-resource-table__qty-input") {
            name = "required"
            min = "1"
            max = "2000000000"
            value = item.required.toString()
            attributes["data-resource-id"] = item.id.toString()
            hxPatch("/worlds/$worldId/projects/$projectId/resources/gathering/${item.id}/required")
            hxTarget("#plan-row-${item.id}")
            hxSwap("outerHTML")
            hxTrigger("change")
        }
    }
    val sourceLabel = when (item.sourceType) {
        "manual" -> "Manual gather"
        "project" -> item.solvedByProject?.second ?: "Unknown project"
        else -> "--"
    }
    td("plan-resource-table__source") {
        span("plan-resource-table__source-badge") { +sourceLabel }
    }
    td("plan-resource-table__action") {
        button(classes = "btn btn--ghost btn--sm plan-resource-table__delete-btn") {
            type = ButtonType.button
            hxDelete("/worlds/$worldId/projects/$projectId/resources/gathering/${item.id}?context=plan")
            hxTarget("#plan-row-${item.id}")
            hxSwap("outerHTML")
            +"×"
        }
    }
}

/**
 * The plan-view resource table. Rendered both inline and as the HTMX swap target for
 * the schematic-upload flow (`outerHTML` swap of `#plan-resource-table`).
 */
fun FlowContent.planResourceTable(worldId: Int, projectId: Int, resources: List<ResourceGatheringItem>) {
    val filteredResources = resources.filter { it.required > 0 }
    table("data-table plan-resource-table") {
        id = "plan-resource-table"
        if (filteredResources.isNotEmpty()) {
            thead {
                tr {
                    th { classes = setOf("plan-resource-table__col-status") }
                    th { classes = setOf("plan-resource-table__col-item"); +"Item" }
                    th { classes = setOf("plan-resource-table__col-qty"); +"Qty" }
                    th { classes = setOf("plan-resource-table__col-source"); +"Source" }
                    th { classes = setOf("plan-resource-table__col-action") }
                }
            }
        }
        tbody {
            id = "plan-resource-table-body"
            filteredResources.forEach { item ->
                tr {
                    planResourceRow(worldId, projectId, item)
                }
            }
        }
    }
}

/** Renders the full plan-view resource table as a standalone HTML fragment (HTMX swap response). */
fun planResourceTableFragment(worldId: Int, projectId: Int, resources: List<ResourceGatheringItem>): String =
    createHTML().table("data-table plan-resource-table") {
        id = "plan-resource-table"
        val filteredResources = resources.filter { it.required > 0 }
        if (filteredResources.isNotEmpty()) {
            thead {
                tr {
                    th { classes = setOf("plan-resource-table__col-status") }
                    th { classes = setOf("plan-resource-table__col-item"); +"Item" }
                    th { classes = setOf("plan-resource-table__col-qty"); +"Qty" }
                    th { classes = setOf("plan-resource-table__col-source"); +"Source" }
                    th { classes = setOf("plan-resource-table__col-action") }
                }
            }
        }
        tbody {
            id = "plan-resource-table-body"
            filteredResources.forEach { item ->
                tr {
                    planResourceRow(worldId, projectId, item)
                }
            }
        }
    }

/**
 * Modal that uploads a Litematica file and replaces the project's resource list with the
 * build's exact material counts. When the project already has resources it warns that
 * the upload replaces them, since a schematic is the complete material list for a build.
 */
fun FlowContent.resourceSchematicModal(worldId: Int, projectId: Int, existingResourceCount: Int) {
    dialog {
        id = "resource-schematic-modal"
        classes = setOf("modal-backdrop")
        div("modal") {
            div("modal__heading") { +"Upload schematic" }
            div("modal__body") {
                if (existingResourceCount > 0) {
                    val noun = if (existingResourceCount == 1) "resource" else "resources"
                    p("modal__warning") {
                        +"This replaces the project's $existingResourceCount existing $noun."
                    }
                }
                form {
                    hxPost("/worlds/$worldId/projects/$projectId/resources/from-schematic")
                    hxTarget("#plan-resource-table")
                    hxSwap("outerHTML")
                    hxTargetError(".form-error")
                    attributes["hx-encoding"] = "multipart/form-data"
                    attributes["hx-on::after-request"] =
                        "if(event.detail.successful) { this.reset(); this.closest('dialog')?.close() }"

                    label {
                        htmlFor = "resource-schematic-file"
                        +"Schematic file"
                        span("required-indicator") { +"*" }
                    }
                    input(classes = "form-control") {
                        id = "resource-schematic-file"
                        type = InputType.file
                        name = "schematicFile"
                        accept = ".litematic"
                        required = true
                    }
                    p("form-error") {
                        id = "validation-error-schematicFile"
                    }

                    div("modal__actions") {
                        button {
                            classes = setOf("btn", "btn--primary")
                            type = ButtonType.submit
                            +"Replace resources"
                        }
                        button {
                            classes = setOf("btn", "btn--ghost")
                            type = ButtonType.button
                            attributes["onclick"] = "this.closest('dialog')?.close()"
                            +"Cancel"
                        }
                    }
                }
            }
        }
    }
}

/** Fragment for detail-content endpoint response (inner content of #project-content). */
fun gatheringPlannerFragment(
    project: Project,
    resources: List<ResourceGatheringItem>,
    tasks: List<ActionTask>,
    plan: GatheringPlan?,
    lens: String = "list",
    progressMap: Map<String, Int> = emptyMap(),
): String = createHTML().div {
    id = "project-content"
    gatheringPlannerContent(project, resources, tasks, plan, lens, progressMap)
}

/** OOB fragment to update #project-progress after task create/complete. */
fun taskProgressOobFragment(completed: Int, total: Int): String =
    createHTML().div {
        id = "project-progress"
        attributes["hx-swap-oob"] = "innerHTML:#project-progress"
        if (total > 0) {
            progressBar(completed, total)
            p("project-detail__overall-progress-label") {
                +"$completed of $total tasks completed"
            }
        }
    }
