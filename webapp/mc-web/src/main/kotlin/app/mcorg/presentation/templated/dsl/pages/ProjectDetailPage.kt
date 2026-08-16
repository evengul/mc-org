package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectProduction
import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.domain.model.task.ActionTask
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.world.World
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.plan.Activity
import app.mcorg.engine.plan.ActivityGroup
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.PlanOverrides
import app.mcorg.engine.plan.SupplySource
import app.mcorg.pipeline.resources.FarmScaleDemand
import app.mcorg.pipeline.resources.FarmScaleDemands
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxDeleteWithConfirm
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxIndicator
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTargetError
import app.mcorg.presentation.hxTrigger
import app.mcorg.presentation.templated.dsl.Link
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
import app.mcorg.pipeline.resources.FeedsLabel
import app.mcorg.pipeline.resources.buildFeedsLabels
import app.mcorg.pipeline.resources.PendingFarmItem
import app.mcorg.pipeline.resources.PendingFarmSupply
import app.mcorg.pipeline.resources.buildNodeIngredients
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

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
    productions: List<ProjectProduction> = emptyList(),
    pendingFarms: List<PendingFarmSupply> = emptyList(),
    drillTarget: TargetTree? = null,
    drillCandidateCounts: Map<String, Int> = emptyMap(),
    drillNodeIngredients: Map<String, String> = emptyMap(),
    drillHighlightItemId: String? = null,
    drillOverrides: PlanOverrides = PlanOverrides.NONE,
    drillGraph: ItemSourceGraph? = null,
    farmScaleThreshold: Int = World.DEFAULT_FARM_SCALE_THRESHOLD,
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
                        projectProductionsField(project, productions, isWorldAdmin)
                    }
                    gatheringOverallProgress(project.id, project.worldId, resources, plan, progressMap)
                }
                if (isWorldAdmin) {
                    div("project-detail__header-right") {
                        projectDeleteButton(project)
                    }
                }
            }

            div {
                id = "project-content"
                if (drillTarget != null) {
                    // ?drill=<item> deep-links straight into a target's chain (reload/share-safe).
                    drillChainContent(project, drillTarget, drillCandidateCounts, drillNodeIngredients, overrides = drillOverrides, graph = drillGraph, highlightItemId = drillHighlightItemId)
                } else {
                    gatheringPlannerContent(project, resources, tasks, plan, lens, progressMap, pendingFarms, farmScaleThreshold, isWorldAdmin)
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
 * Delete-project affordance shown next to the edit fields (admins only — gated by the
 * caller). Uses the shared type-to-confirm delete dialog (hxDeleteWithConfirm); the
 * server redirects back to the world's project list on success.
 */
private fun FlowContent.projectDeleteButton(project: Project) {
    button(classes = "btn btn--danger btn--sm") {
        type = ButtonType.button
        hxDeleteWithConfirm(
            url = Link.Worlds.world(project.worldId).project(project.id).to,
            title = "Delete project",
            description = "This action cannot be undone. All tasks, resources, and progress for this project will be permanently deleted.",
            warning = "Warning: This will permanently delete \"${project.name}\" and all associated data.",
            confirmText = project.name,
        )
        +"Delete project"
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
    pendingFarms: List<PendingFarmSupply> = emptyList(),
    farmScaleThreshold: Int = World.DEFAULT_FARM_SCALE_THRESHOLD,
    isWorldAdmin: Boolean = false,
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
        else -> listLensContent(project, resources, tasks, plan, progressMap, pendingFarms, farmScaleThreshold, isWorldAdmin)
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
 * The List lens. Renders two resolutions of the same plan behind a client-side toggle
 * (MCO-226): "What I need" (the targets the user defined) and "How to make it" (the engine's
 * full dependency breakdown). Both bodies are rendered; the toggle shows one at a time and its
 * choice is persisted in sessionStorage so it survives #project-content re-renders (e.g. the
 * inline variant pick, which re-renders the whole list via origin=list). The Tasks section
 * sits below both and is always visible.
 */
private fun FlowContent.listLensContent(
    project: Project,
    resources: List<ResourceGatheringItem>,
    tasks: List<ActionTask>,
    plan: GatheringPlan?,
    progressMap: Map<String, Int> = emptyMap(),
    pendingFarms: List<PendingFarmSupply> = emptyList(),
    farmScaleThreshold: Int = World.DEFAULT_FARM_SCALE_THRESHOLD,
    isWorldAdmin: Boolean = false,
) {
    // Resolution toggle (client-side; default "targets" applied by plan-view.js).
    listResolutionToggle()

    // "What I need" — the defined targets, with the add/upload definition controls.
    div("list-resolution-view") {
        id = "list-targets-view"
        attributes["data-resolution-view"] = "targets"

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

            // Resource table + ignored section — always rendered as HTMX swap target
            planResourcesArea(project.worldId, project.id, resources)

            // Schematic upload modal
            resourceSchematicModal(project.worldId, project.id, resources.count { it.required > 0 && !it.ignored })
        }
    }

    // "How to make it" — the engine's full dependency breakdown (grouped activities).
    // Hidden by default (targets is the default resolution); plan-view.js reveals it when
    // the persisted choice is "breakdown", avoiding a both-views flash before JS runs.
    div("list-resolution-view list-resolution-view--hidden") {
        id = "list-breakdown-view"
        attributes["data-resolution-view"] = "breakdown"

        gatheringPlanSections(project, plan, progressMap, pendingFarms, farmScaleThreshold, isWorldAdmin)
    }

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
 * Segmented toggle between the two List-lens resolutions (MCO-226). Plain buttons wired by
 * plan-view.js (no server round-trip — both views are already in the DOM). The active state
 * and which view is shown are driven client-side and persisted per project in sessionStorage,
 * so the choice survives #project-content re-renders. "What I need" is the default.
 */
private fun FlowContent.listResolutionToggle() {
    div("list-resolution") {
        attributes["role"] = "tablist"
        attributes["aria-label"] = "Plan resolution"
        button(classes = "list-resolution__option list-resolution__option--active") {
            type = ButtonType.button
            attributes["data-resolution"] = "targets"
            attributes["role"] = "tab"
            attributes["aria-selected"] = "true"
            +"What I need"
        }
        button(classes = "list-resolution__option") {
            type = ButtonType.button
            attributes["data-resolution"] = "breakdown"
            attributes["role"] = "tab"
            attributes["aria-selected"] = "false"
            +"How to make it"
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
    pendingFarms: List<PendingFarmSupply> = emptyList(),
    farmScaleThreshold: Int = World.DEFAULT_FARM_SCALE_THRESHOLD,
    isWorldAdmin: Boolean = false,
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
    val nodeIngredients = buildNodeIngredients(plan)
    val feedsLabels = buildFeedsLabels(plan)
    val farmScale = FarmScaleDemands.of(plan, farmScaleThreshold)
    val farmScaleIds = farmScale.mapTo(mutableSetOf()) { it.itemId }

    div {
        id = "gathering-plan-sections"

        // Above the work sections on purpose: this is not a step in the plan, it is the answer
        // to "what should I build first", and it is what turns one import into a roadmap.
        farmScaleRollUp(farmScale, farmScaleThreshold, project.worldId, isWorldAdmin)

        groupOrder.forEach { group ->
            val activities = byGroup[group] ?: return@forEach
            if (activities.isEmpty()) return@forEach

            // Independent groups (raw activities with no intra-group ingredient edges) sort
            // biggest-pile-first so gathering is prioritised by quantity. Smelt/Craft keep the
            // engine's topological order so an ingredient still precedes what it builds.
            val ordered = if (group in QUANTITY_SORTED_GROUPS) {
                activities.sortedWith(compareByDescending<Activity> { it.quantity }.thenBy { it.item.name })
            } else {
                activities
            }

            div("project-detail__section") {
                span("section-label") { +groupLabel(group) }
                if (group == ActivityGroup.NEEDS_ATTENTION) {
                    needsAttentionList(project, ordered)
                } else {
                    div("resource-list") {
                        ordered.forEach { activity ->
                            planActivityRow(
                                project.worldId,
                                project.id,
                                activity,
                                progressMap,
                                nodeIngredients,
                                feedsLabels,
                                isFarmScale = activity.item.id in farmScaleIds,
                            )
                        }
                    }
                }
            }
        }

        pendingFarmNotice(project.worldId, pendingFarms)
    }
}

/**
 * The farm-scale roll-up (MCO-401): raw materials whose demand is large enough to be worth a
 * farm, largest first.
 *
 * This list *is* the roadmap-building input — each line is a candidate prerequisite farm
 * project — which is why it leads the plan rather than sitting at the bottom with the notices.
 * Ordering carries the meaning: the top line is where to start.
 *
 * It names quantities and nothing else. Suggesting *which* farm to build is MCO-294 and needs
 * an idea bank; saying "this is farm-scale" needs only the plan, which is why the two shipped
 * apart. Items an operational farm already supplies never appear — they are solved, not
 * suggestions (see [FarmScaleDemands]).
 */
private fun FlowContent.farmScaleRollUp(
    demands: List<FarmScaleDemand>,
    threshold: Int,
    worldId: Int,
    canEditThreshold: Boolean,
) {
    if (demands.isEmpty()) return

    div("plan-farm-scale") {
        id = "plan-farm-scale"
        span("section-label") { +"Worth a farm" }
        p("plan-farm-scale__lead") {
            +"${demands.size} raw ${if (demands.size == 1) "material needs" else "materials need"} more than "
            // The number is the judgement this whole list rests on, so it is the thing to edit:
            // disagreeing with the list means disagreeing with the threshold. Admin-only, matching
            // how every other route to world settings is gated (settingsHref in Navigation.kt) —
            // a link that 403s is worse than no link.
            if (canEditThreshold) {
                a(classes = "plan-farm-scale__threshold") {
                    href = "/worlds/$worldId/settings"
                    title = "Change this world's farm-scale threshold"
                    +"%,d".format(threshold)
                }
            } else {
                +"%,d".format(threshold)
            }
            +" — each is a candidate for its own farm project."
        }
        div("plan-farm-scale__list") {
            demands.forEach { demand ->
                div("plan-farm-scale__item") {
                    span("plan-farm-scale__quantity") { +"%,d".format(demand.quantity) }
                    span("plan-farm-scale__name") { +demand.itemName }
                }
            }
        }
    }
}

/**
 * The partial-dependency notice (MCO-299): items this plan still gathers by hand that a
 * farm project in the world has promised but is not producing yet.
 *
 * Bottom of the plan, deliberately low visual weight — it changes nothing about what to do
 * today, it only says the manual work is a stopgap. It appears solely for farms that are
 * *not* operational; once one is Done, its items move into "Collect from farms" and the
 * line for them disappears on its own.
 */
private fun FlowContent.pendingFarmNotice(worldId: Int, pendingFarms: List<PendingFarmSupply>) {
    if (pendingFarms.isEmpty()) return

    div("plan-pending-farms") {
        id = "plan-pending-farms"
        pendingFarms.forEach { farm ->
            p("plan-pending-farms__line") {
                +itemsPhrase(farm.items)
                +" will come from "
                a(classes = "plan-pending-farms__project") {
                    href = "/worlds/$worldId/projects/${farm.projectId}"
                    +farm.projectName
                }
                +" once it is running — gather "
                +(if (farm.items.size == 1) "it" else "them")
                +" manually meanwhile."
            }
        }
    }
}

/** "32 Iron Ingot", "32 Iron Ingot and 12 Gold Ingot", "32 Iron Ingot, 12 Gold Ingot and 4 Diamond". */
private fun itemsPhrase(items: List<PendingFarmItem>): String {
    val parts = items.map { "${it.quantity} ${it.itemName}" }
    return when (parts.size) {
        1 -> parts.first()
        2 -> "${parts[0]} and ${parts[1]}"
        else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }
}

/**
 * Groups whose rows carry no ingredient edges between each other, so they can be sorted
 * by quantity for prioritisation without breaking the "ingredients before consumers" reading
 * that Smelt/Craft rely on.
 */
private val QUANTITY_SORTED_GROUPS = setOf(
    ActivityGroup.COLLECT_SUPPLIED,
    ActivityGroup.GATHER,
    ActivityGroup.HUNT,
    ActivityGroup.LOOT,
    ActivityGroup.TRADE,
)

/** Renders a single activity row. Presentation depends on status. */
private fun FlowContent.planActivityRow(
    worldId: Int,
    projectId: Int,
    activity: Activity,
    progressMap: Map<String, Int> = emptyMap(),
    nodeIngredients: Map<String, String> = emptyMap(),
    feedsLabels: Map<String, FeedsLabel> = emptyMap(),
    isFarmScale: Boolean = false,
) {
    when (activity.status) {
        PlanNodeStatus.SUPPLIED -> suppliedActivityRow(worldId, projectId, activity, feedsLabels[activity.item.id])
        PlanNodeStatus.OPEN_TAG -> openTagActivityRow(worldId, projectId, activity)
        PlanNodeStatus.BLOCKED -> blockedActivityRow(worldId, projectId, activity)
        PlanNodeStatus.RESOLVED, PlanNodeStatus.RAW_GATHER ->
            counterActivityRow(
                worldId,
                projectId,
                activity,
                progressMap,
                nodeIngredients,
                feedsLabels[activity.item.id],
                isFarmScale = isFarmScale,
            )
    }
}

/** Renders the "Feeds 24 Birch Door · 40 Chest" reverse-provenance line, when present. */
internal fun FlowContent.feedsLine(label: FeedsLabel?) {
    if (label == null) return
    div("resource-row__feeds") {
        label.title?.let { attributes["title"] = it }
        +label.text
    }
}

/**
 * SUPPLIED row: badge + supply label, no counter.
 *
 * Farm supply and linked-project supply share the group but are not the same promise
 * (MCO-299): a farm keeps producing, a linked project hands over once. The badge says
 * which, and a linked project's name is a link to it — the farm's name is not, because
 * the supply is ambient (any operational producer of the item, resolved at plan time).
 */
private fun FlowContent.suppliedActivityRow(
    worldId: Int,
    projectId: Int,
    activity: Activity,
    feedsLabel: FeedsLabel? = null,
) {
    val supply = activity.supply
    val encodedItemId = URLEncoder.encode(activity.item.id, StandardCharsets.UTF_8)
    div("resource-row") {
        id = "plan-activity-${activity.item.id.replace(":", "-")}"
        div("resource-row__desktop") {
            div("resource-row__name") { +activity.item.name }
            when (supply) {
                is SupplySource.Farm -> {
                    span("badge badge--accent") { +"Farm" }
                    span("resource-row__source") { +"from ${supply.label}" }
                }
                is SupplySource.LinkedProject -> {
                    span("badge badge--accent") { +"Project" }
                    span("resource-row__source") {
                        +"from "
                        a(classes = "resource-row__source-link") {
                            href = "/worlds/$worldId/projects/${supply.projectId}"
                            +supply.label
                        }
                    }
                }
                null -> span("badge badge--accent") { +"Supplied" }
            }
            drillButton(worldId, projectId, encodedItemId)
        }
        feedsLine(feedsLabel)
    }
}

/**
 * "Needs attention", ordered and collapsed (MCO-400).
 *
 * The section used to render every unresolved question as an equal amber callout in id order.
 * On the YAMS import that is 25 stacked warnings opening with "Charcoal or Coal" (2 items) and
 * burying "Planks" (110,824 items — 96% of all the material behind these questions) at
 * position 19. The count was never the real problem: the questions are already one per tag, not
 * one per consumer. The problem is that a two-item decision and a hundred-thousand-item decision
 * look exactly alike, in an order that has nothing to do with either.
 *
 * So: blocked rows first, since they cannot be answered by picking at all and no amount of
 * variant-choosing moves them; then the questions by how much material each decides, with the
 * tail folded away.
 */
private fun FlowContent.needsAttentionList(project: Project, activities: List<Activity>) {
    val blocked = activities.filter { it.status == PlanNodeStatus.BLOCKED }
    val questions = activities
        .filter { it.status == PlanNodeStatus.OPEN_TAG }
        .sortedWith(compareByDescending<Activity> { it.quantity }.thenBy { it.item.name })

    div("resource-list") {
        blocked.forEach { blockedActivityRow(project.worldId, project.id, it) }
    }

    if (questions.isEmpty()) return

    val leadCount = leadingQuestionCount(questions)
    val lead = questions.take(leadCount)
    val rest = questions.drop(leadCount)

    div("plan-attention") {
        id = "plan-attention"
        p("plan-attention__lead") { +attentionLead(questions, leadCount) }
        div("resource-list") {
            lead.forEach { openTagActivityRow(project.worldId, project.id, it) }
        }
        if (rest.isNotEmpty()) {
            details("plan-attention__rest") {
                summary {
                    span("btn btn--ghost btn--sm plan-attention__toggle") {
                        span("plan-attention__toggle--closed") { +"Show ${rest.size} smaller choices ▾" }
                        span("plan-attention__toggle--open") { +"Hide smaller choices ▴" }
                    }
                }
                div("resource-list") {
                    rest.forEach { openTagActivityRow(project.worldId, project.id, it) }
                }
            }
        }
    }
}

/**
 * How many questions to show before folding the rest away.
 *
 * Enough to cover [ATTENTION_COVERAGE] of the material the questions decide, capped at
 * [MAX_LEADING_QUESTIONS] — coverage alone would expand nearly everything when demand happens to
 * be spread evenly, which is the wall again. Always at least one: a section whose every question
 * is hidden behind a toggle reads as an empty section.
 *
 * A remainder of one or two is not worth a fold, so those stay open.
 */
private fun leadingQuestionCount(questionsByQuantityDesc: List<Activity>): Int {
    val total = questionsByQuantityDesc.sumOf { it.quantity }
    if (total <= 0) return questionsByQuantityDesc.size.coerceAtMost(MAX_LEADING_QUESTIONS)

    var covered = 0L
    var count = 0
    for (activity in questionsByQuantityDesc) {
        covered += activity.quantity
        count++
        if (count >= MAX_LEADING_QUESTIONS) break
        if (covered.toDouble() / total >= ATTENTION_COVERAGE) break
    }
    return if (questionsByQuantityDesc.size - count < MIN_FOLDED) questionsByQuantityDesc.size else count
}

/**
 * "25 variant choices — these 1 decide 96% of the material behind them."
 *
 * The percentage is the point: it is what tells you that answering the top question is most of
 * the work, and that the twenty below it are detail. Stated only when something is actually
 * folded away, and only when the quantities can support the claim.
 */
private fun attentionLead(questions: List<Activity>, leadCount: Int): String {
    val plural = if (questions.size == 1) "variant choice" else "variant choices"
    if (leadCount >= questions.size) return "${questions.size} $plural — pick to sharpen the plan."

    val total = questions.sumOf { it.quantity }
    if (total <= 0) return "${questions.size} $plural — largest first."

    val covered = questions.take(leadCount).sumOf { it.quantity }
    val percent = (covered * 100.0 / total).roundToInt()
    val these = if (leadCount == 1) "the first decides" else "these $leadCount decide"
    return "${questions.size} $plural — $these $percent% of the material behind them."
}

/** Show questions until they cover this share of the material the whole section decides. */
private const val ATTENTION_COVERAGE = 0.9

/** Never lead with more than this many, however flat the distribution. */
private const val MAX_LEADING_QUESTIONS = 5

/** A remainder smaller than this is not worth hiding behind a toggle. */
private const val MIN_FOLDED = 3

/** OPEN_TAG row: amber callout, indicates variant pick needed. */
private fun FlowContent.openTagActivityRow(worldId: Int, projectId: Int, activity: Activity) {
    val encodedItemId = URLEncoder.encode(activity.item.id, StandardCharsets.UTF_8)
    val pickerSlotId = "picker-${activity.item.id.replace(Regex("[^a-zA-Z0-9]"), "-")}"
    div("callout callout--warning") {
        id = "plan-activity-${activity.item.id.replace(":", "-")}"
        span("callout__icon") { +"!" }
        div("callout__body") {
            // The quantity leads (MCO-400). Without it every question looks alike, and on a real
            // import they are not: on the YAMS build one choice carries 110,824 items and the
            // next-but-three carries 2. Same words, four orders of magnitude apart.
            span("plan-attention__quantity") { +"%,d".format(activity.quantity) }
            +" "
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
    nodeIngredients: Map<String, String> = emptyMap(),
    feedsLabel: FeedsLabel? = null,
    isFarmScale: Boolean = false,
) {
    val itemSlug = activity.item.id.replace(":", "-")
    val rowId = "plan-activity-$itemSlug"
    val required = activity.quantity
    val current = (progressMap[activity.item.id] ?: 0).toLong().coerceIn(0, required)
    val percent = if (required > 0) (current * 100 / required).toInt() else 0
    // Method + detail: ingredients for recipes ("Smelting · 1 Raw Iron"), or the loot location
    // for a pinned loot source ("Chest Loot · Desert pyramid") — the relationship/source,
    // visible without drilling.
    val detail = nodeIngredients[activity.item.id] ?: activity.source?.let { lootTableName(it) }
    val sourceLabel = listOfNotNull(activity.source?.getMethodLabel(), detail)
        .joinToString(" · ")
        .ifEmpty { null }
    val encodedItemId = URLEncoder.encode(activity.item.id, StandardCharsets.UTF_8)

    div("resource-row") {
        id = rowId
        attributes["data-item-name"] = activity.item.name
        attributes["data-progress-pct"] = percent.toString()
        attributes["data-required"] = required.toString()

        div("resource-row__desktop") {
            div("resource-row__name") { +activity.item.name }

            // MCO-401: says the quantity is farm-scale, not which farm — that is MCO-294 and
            // needs an idea bank. On the row rather than only in the roll-up, so the judgement
            // is visible while reading the gathering work itself.
            if (isFarmScale) {
                span("badge plan-farm-scale__badge") {
                    attributes["title"] = "More than this world's farm-scale threshold — worth a farm"
                    +"Farm-scale"
                }
            }

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
        feedsLine(feedsLabel)
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
        // Marks the drill entry point so plan-view.js can remember the page's scroll
        // position and restore it when "< Back to plan" (DrillView.kt) returns here.
        attributes["data-drill-nav"] = "in"
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
    td("plan-resource-table__action") {
        div("plan-resource-table__action-group") {
            button(classes = "btn btn--ghost btn--sm plan-resource-table__ignore-btn") {
                type = ButtonType.button
                attributes["title"] = "Ignore — exclude from the material list and gathering plan"
                attributes["aria-label"] = "Ignore ${item.name}"
                hxPatch("/worlds/$worldId/projects/$projectId/resources/gathering/${item.id}/ignore")
                hxTarget("#plan-resources-area")
                hxSwap("outerHTML")
                +"⊘"
            }
            button(classes = "btn btn--ghost btn--sm plan-resource-table__delete-btn") {
                type = ButtonType.button
                hxDelete("/worlds/$worldId/projects/$projectId/resources/gathering/${item.id}?context=plan")
                hxTarget("#plan-row-${item.id}")
                hxSwap("outerHTML")
                +"×"
            }
        }
    }
}

/** Un-ignore action row: shown in the ignored section (MCO-247), reverses the ignore toggle. */
fun TR.ignoredResourceRow(worldId: Int, projectId: Int, item: ResourceGatheringItem) {
    id = "plan-ignored-row-${item.id}"
    attributes["data-resource-id"] = item.id.toString()

    td("plan-resource-table__status") {
        span("status-dot status-dot--unset") {}
    }
    td("plan-resource-table__item") {
        +item.name
    }
    td("plan-resource-table__qty") {
        span("plan-resource-table__qty-display") {
            +item.required.toString()
        }
    }
    td("plan-resource-table__action") {
        button(classes = "btn btn--ghost btn--sm plan-resource-table__unignore-btn") {
            type = ButtonType.button
            attributes["title"] = "Un-ignore — include back in the material list and gathering plan"
            hxPatch("/worlds/$worldId/projects/$projectId/resources/gathering/${item.id}/ignore")
            hxTarget("#plan-resources-area")
            hxSwap("outerHTML")
            +"Un-ignore"
        }
    }
}

/**
 * The plan-view resource table. Rendered both inline and as the HTMX swap target for
 * the schematic-upload flow (`outerHTML` swap of `#plan-resource-table`).
 */
fun FlowContent.planResourceTable(worldId: Int, projectId: Int, resources: List<ResourceGatheringItem>) {
    val filteredResources = resources.filter { it.required > 0 && !it.ignored }
    table("data-table plan-resource-table") {
        id = "plan-resource-table"
        if (filteredResources.isNotEmpty()) {
            thead {
                tr {
                    th { classes = setOf("plan-resource-table__col-status") }
                    th { classes = setOf("plan-resource-table__col-item"); +"Item" }
                    th { classes = setOf("plan-resource-table__col-qty"); +"Qty" }
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
        val filteredResources = resources.filter { it.required > 0 && !it.ignored }
        if (filteredResources.isNotEmpty()) {
            thead {
                tr {
                    th { classes = setOf("plan-resource-table__col-status") }
                    th { classes = setOf("plan-resource-table__col-item"); +"Item" }
                    th { classes = setOf("plan-resource-table__col-qty"); +"Qty" }
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
 * Wraps the active resource table and the ignored-items section (MCO-247) in a single
 * HTMX swap target — an ignore/un-ignore toggle moves a row between the two, so both
 * are re-rendered together.
 */
fun FlowContent.planResourcesArea(worldId: Int, projectId: Int, resources: List<ResourceGatheringItem>) {
    div {
        id = "plan-resources-area"
        planResourceTable(worldId, projectId, resources)
        ignoredResourcesSection(worldId, projectId, resources)
    }
}

/** Standalone HTML fragment version of [planResourcesArea] (HTMX swap response for the ignore toggle). */
fun planResourcesAreaFragment(worldId: Int, projectId: Int, resources: List<ResourceGatheringItem>): String =
    createHTML().div {
        id = "plan-resources-area"
        planResourceTable(worldId, projectId, resources)
        ignoredResourcesSection(worldId, projectId, resources)
    }

/**
 * Ignored-items section (MCO-247): items the user excluded from the material list and
 * gathering plan, kept visible and reversible via an "Un-ignore" action. Renders nothing
 * when there are no ignored items, so it doesn't clutter the page for the common case.
 */
fun FlowContent.ignoredResourcesSection(worldId: Int, projectId: Int, resources: List<ResourceGatheringItem>) {
    val ignoredResources = resources.filter { it.ignored }
    if (ignoredResources.isEmpty()) return

    div("project-detail__section plan-ignored-section") {
        id = "plan-ignored-section"
        div("project-detail__section-header") {
            span("project-detail__section-title section-label") { +"Ignored (${ignoredResources.size})" }
        }
        table("data-table plan-resource-table plan-resource-table--ignored") {
            id = "plan-ignored-table"
            tbody {
                id = "plan-ignored-table-body"
                ignoredResources.forEach { item ->
                    tr {
                        ignoredResourceRow(worldId, projectId, item)
                    }
                }
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
                    hxIndicator("#resource-schematic-progress")
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

                    // Upload/parse feedback: hidden until the request is in flight (see
                    // .htmx-indicator in modal.css) — large schematics can take a while to
                    // parse and there's otherwise no visible sign of progress.
                    div("modal__progress htmx-indicator") {
                        id = "resource-schematic-progress"
                        div("modal__progress-spinner") {}
                        span { +"Parsing schematic…" }
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
    pendingFarms: List<PendingFarmSupply> = emptyList(),
    farmScaleThreshold: Int = World.DEFAULT_FARM_SCALE_THRESHOLD,
    isWorldAdmin: Boolean = false,
): String = createHTML().div {
    id = "project-content"
    gatheringPlannerContent(
        project, resources, tasks, plan, lens, progressMap, pendingFarms, farmScaleThreshold, isWorldAdmin,
    )
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
