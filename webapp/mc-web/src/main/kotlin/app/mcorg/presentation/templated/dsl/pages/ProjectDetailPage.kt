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
import app.mcorg.pipeline.resources.FarmDismissal
import app.mcorg.pipeline.resources.FarmScaleDemand
import app.mcorg.pipeline.resources.FarmScaleDemands
import app.mcorg.pipeline.project.SELECTED_DESIGN_FIELD
import app.mcorg.pipeline.resources.FarmSuggestion
import app.mcorg.pipeline.resources.FarmSuggestionChoice
import app.mcorg.pipeline.resources.FarmSuggestionChoices
import app.mcorg.pipeline.resources.RecommendationReason
import app.mcorg.presentation.templated.dsl.formatPlainCount
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxDeleteWithConfirm
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxIndicator
import app.mcorg.presentation.hxOutOfBands
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
import kotlinx.html.summary
import kotlinx.html.details
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
    farmSuggestions: List<FarmSuggestion> = emptyList(),
    /** Stored ids this world's Minecraft version has no catalog entry for (MCO-157). */
    versionGaps: Set<String> = emptySet(),
    /** Farm-scale demand this world has decided against (MCO-407). */
    farmDismissals: List<FarmDismissal> = emptyList(),
): String = pageShell(
    pageTitle = "Seam — ${project.name}",
    user = user,
    stylesheets = listOf(
        "/static/styles/components/btn.css",
        "/static/styles/components/form.css",
        "/static/styles/components/item-search.css",
        "/static/styles/components/item-glyph.css",
        "/static/styles/components/badge.css",
        "/static/styles/components/progress.css",
        "/static/styles/components/resource-row.css",
        "/static/styles/components/work-row.css",
        "/static/styles/components/task-list.css",
        "/static/styles/components/resource-search.css",
        "/static/styles/components/resource-panel.css",
        "/static/styles/components/callout.css",
        "/static/styles/components/drill.css",
        "/static/styles/pages/project-detail.css",
        "/static/styles/components/farm-panel.css",
    ),
    scripts = listOf(
        "/static/scripts/resource-search.js",
        "/static/scripts/plan-view.js",
        "/static/scripts/resource-panel.js",
        "/static/scripts/farm-suggestions.js"
    )
) {
    appHeader(
        // Without this the mobile header falls back to "Seam" — and since MCO-474 made that
        // name the only way back out of a project on a phone, it was a link labelled with the
        // product name that went to a world.
        worldName = worldName,
        worldId = project.worldId,
        projectId = project.id,
        user = user,
        isWorldAdmin = isWorldAdmin,
        breadcrumbBlock = {
            link("Worlds", "/worlds")
                .link(worldName, "/worlds/${project.worldId}/roadmap")
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
                    gatheringPlannerContent(project, resources, tasks, plan, progressMap, pendingFarms, farmScaleThreshold, farmSuggestions, versionGaps, isWorldAdmin, farmDismissals)
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
    // SUPPLIED counts: emptying a farm is work with a quantity, and leaving it out meant the
    // header read 0% while you hauled 74,557 cobblestone. OPEN_TAG and BLOCKED still do not —
    // their quantities are provisional until the question is answered.
    val countable = plan.activityList.filter {
        it.status == PlanNodeStatus.RESOLVED ||
            it.status == PlanNodeStatus.RAW_GATHER ||
            it.status == PlanNodeStatus.SUPPLIED
    }
    val totalRequired = countable.sumOf { it.quantity }
    val totalCollected = countable.sumOf { activity ->
        (progressMap[activity.item.id] ?: 0).toLong()
    }
    return totalRequired to totalCollected
}

/**
 * The gathering planner.
 *
 * The lens pills (List / Next up / Sessions) are gone (MCO-481). Two of the three were stubs
 * rendering "coming soon", so the strip was a whole level of navigation carrying one real
 * destination. Next up is a widget below instead — the answer is worth having *while* you work,
 * not somewhere you have to go. Sessions stays unbuilt: MCO-224 specs it as geography trips and
 * MCO-225 says that data does not exist yet, so it would render its own fallback.
 */
fun FlowContent.gatheringPlannerContent(
    project: Project,
    resources: List<ResourceGatheringItem>,
    tasks: List<ActionTask>,
    plan: GatheringPlan?,
    progressMap: Map<String, Int> = emptyMap(),
    pendingFarms: List<PendingFarmSupply> = emptyList(),
    farmScaleThreshold: Int = World.DEFAULT_FARM_SCALE_THRESHOLD,
    farmSuggestions: List<FarmSuggestion> = emptyList(),
    /** Stored ids this world's Minecraft version has no catalog entry for (MCO-157). */
    versionGaps: Set<String> = emptySet(),
    isWorldAdmin: Boolean = false,
    /** Farm-scale demand this world has decided against (MCO-407). */
    farmDismissals: List<FarmDismissal> = emptyList(),
) {
    // Next up only speaks once the questions are answered (MCO-504).
    //
    // Even's framing: "what's next is mostly relevant AFTER the questions have been answered.
    // When those questions are there, they are the most important thing, and now they're asked
    // in two different places." Both halves matter. The duplication was the visible problem —
    // the same variant question rendered here and in "Needs attention" — but the reason to
    // sequence them rather than just de-duplicate is that this widget's advice is *provisional*
    // while a question is open: answering one redirects the tag to a member and merges its
    // demand, so the largest remaining pile can genuinely change. "Mine 64 cobblestone" is
    // advice about a plan that may not survive the next click.
    //
    // Gated on OPEN_TAG alone, not the whole NEEDS_ATTENTION group. A BLOCKED node also needs
    // the user, but it does not make the rest of the plan provisional — its chain is known, it
    // simply has no source — so a plan that is merely blocked somewhere still has real work to
    // point at.
    //
    // Which questions count as making it provisional: the ones that could actually change the
    // answer, and no felt constant is needed to say which those are.
    //
    // The first cut suppressed the widget for *any* open question. On the real YAMS plan that
    // meant a 4-item choice between red sand and sand hid the whole widget on a build of
    // 400,000 items, which is not caution, it is noise. The obvious repair — "ignore questions
    // below N% of the plan" — invents exactly the kind of threshold MCO-490 exists to remove.
    //
    // There is a derived test instead. Next up claims one thing: the largest outstanding piece
    // of work. Answering a question redirects its tag to a member and merges that demand into
    // whatever else needs the member, so a question can only change that claim if the material
    // it decides could exceed the current top pick. Compare the two directly:
    //
    //     largest open question >= what Next up is currently pointing at   ->  provisional
    //
    // On YAMS the remaining questions are 4, 3 and 2 items against a top pick of 74,692, so the
    // widget speaks. Before the two large ones were answered, `#planks` at 110,824 would have
    // exceeded it and it would have stayed quiet. Conservative in the right direction — it
    // compares against the *unmerged* question quantity, so it errs towards silence — and it
    // needs no number anyone has to defend.
    //
    // MCO-410 (auto-resolving questions that barely affect the plan) is still worth doing; this
    // stops the tail being a UI problem in the meantime, without pre-empting how it answers.
    val candidates = NextUpPick.of(plan, progressMap)
    val largestQuestion = plan?.activityList
        ?.filter { it.status == PlanNodeStatus.OPEN_TAG }
        ?.maxOfOrNull { it.quantity } ?: 0L
    val topPick = candidates.firstOrNull()?.quantity ?: 0L
    if (largestQuestion < topPick) nextUpWidget(project, candidates)
    listLensContent(project, resources, tasks, plan, progressMap, pendingFarms, farmScaleThreshold, farmSuggestions, versionGaps, isWorldAdmin, farmDismissals)
}

/**
 * "Next up" — one move, with a way to ask for another.
 *
 * Every candidate is rendered and all but one hidden, so cycling is a class swap rather than a
 * round trip; there is no server state to keep and nothing to lose on a re-render. See
 * [NextUpPick] for why the order is what it is.
 */
private fun FlowContent.nextUpWidget(
    project: Project,
    candidates: List<Activity>,
) {
    if (candidates.isEmpty()) return

    div("next-up") {
        id = "next-up"
        div("next-up__head") {
            span("section-label") { +"NEXT UP" }
            if (candidates.size > 1) {
                button(classes = "btn btn--ghost btn--sm next-up__shuffle") {
                    id = "next-up-shuffle"
                    type = ButtonType.button
                    +"Something else ↻"
                }
            }
        }
        candidates.forEachIndexed { index, activity ->
            div(if (index == 0) "next-up__card" else "next-up__card next-up__card--hidden") {
                attributes["data-next-up-index"] = index.toString()
                div("next-up__what") {
                    span("next-up__qty") { +formatPlainCount(activity.quantity) }
                    a(classes = "next-up__item") {
                        href = "/worlds/${project.worldId}/projects/${project.id}" +
                            "?drill=" + URLEncoder.encode(activity.item.id, StandardCharsets.UTF_8)
                        +activity.item.name
                    }
                }
                div("next-up__why") { +NextUpPick.reasonFor(activity, index == 0) }

            }
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
    farmSuggestions: List<FarmSuggestion> = emptyList(),
    /** Stored ids this world's Minecraft version has no catalog entry for (MCO-157). */
    versionGaps: Set<String> = emptySet(),
    isWorldAdmin: Boolean = false,
    /** Farm-scale demand this world has decided against (MCO-407). */
    farmDismissals: List<FarmDismissal> = emptyList(),
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
            planResourcesArea(project.worldId, project.id, resources, plan)

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

        gatheringPlanSections(project, plan, progressMap, pendingFarms, farmScaleThreshold, farmSuggestions, versionGaps, isWorldAdmin, farmDismissals)
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
    farmSuggestions: List<FarmSuggestion> = emptyList(),
    /** Stored ids this world's Minecraft version has no catalog entry for (MCO-157). */
    versionGaps: Set<String> = emptySet(),
    isWorldAdmin: Boolean = false,
    /** Farm-scale demand this world has decided against (MCO-407). */
    farmDismissals: List<FarmDismissal> = emptyList(),
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
    // One rule, three readings: the roll-up's lines, the badge on every work row, and what the
    // ignored fold is currently suppressing. A dismissed item is not classified at all, so none
    // of the three can disagree about it (MCO-407).
    val dismissedIds = farmDismissals.mapTo(mutableSetOf()) { it.itemId }
    val farmScale = FarmScaleDemands.of(plan, farmScaleThreshold, dismissedIds)
    val farmScaleIds = farmScale.mapTo(mutableSetOf()) { it.itemId }
    val suppressed = FarmScaleDemands.dismissedIn(plan, farmScaleThreshold, dismissedIds)
    val planTotal = plan.activityList.sumOf { it.quantity }

    div {
        id = "gathering-plan-sections"

        // Above the work sections on purpose: this is not a step in the plan, it is the answer
        // to "what should I build first", and it is what turns one import into a roadmap.
        farmScaleSection(
            farmScale, farmSuggestions, farmScaleThreshold, project.worldId, project.id, isWorldAdmin,
            farmDismissals, suppressed,
        )

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
                    needsAttentionList(project, ordered, versionGaps)
                } else {
                    workSection(
                        project,
                        ordered,
                        progressMap,
                        nodeIngredients,
                        feedsLabels,
                        farmScaleIds,
                        planTotal,
                    )
                }
            }
        }

        pendingFarmNotice(project.worldId, pendingFarms)
    }
}


/**
 * One work section: a panel of 40px lines, with the small jobs as chips in its footer.
 *
 * Two folds do different jobs here and both are needed. [ActivitySectionLayout] decides *which*
 * lines are worth a row — the Craft section is 440 rows and seventeen of them carry 90% of the
 * material. The tail it folds away is not hidden behind a toggle any more but rendered as
 * tick-off chips, because "1 Black Terracotta" is a real errand that wants ticking and not a row
 * with six steppers on it.
 *
 * Both halves keep the order they arrived in, so Smelt and Craft still read ingredients-first.
 */
private fun FlowContent.workSection(
    project: Project,
    ordered: List<Activity>,
    progressMap: Map<String, Int>,
    nodeIngredients: Map<String, String>,
    feedsLabels: Map<String, FeedsLabel>,
    farmScaleIds: Set<String>,
    planTotal: Long,
) {
    val split = ActivitySectionLayout.of(ordered, planTotal)
    fun stateOf(activity: Activity) =
        workRowStateOf(activity, progressMap, nodeIngredients, feedsLabels, farmScaleIds)

    div("work-panel") {
        split.lead.forEach { activity ->
            workRowCollapsed(project.worldId, project.id, stateOf(activity))
        }

        if (split.folded.isNotEmpty()) {
            smallJobsStrip(project.worldId, project.id, split.folded.map(::stateOf))
        }
    }
}

/** How many chips before the strip itself becomes the wall it is replacing. */
private const val SMALL_JOB_CHIPS = 24

/**
 * The footer band: everything too small to deserve a line, as chips you tick as you pass.
 *
 * A chip is the whole tick target — no steppers, because a job of three wool has no use for
 * `-1728`. Craft's tail is 439 of these, so the strip caps itself and says how many it is not
 * showing rather than becoming a second wall.
 */
private fun FlowContent.smallJobsStrip(worldId: Int, projectId: Int, jobs: List<WorkRowState>) {
    val shown = jobs.take(SMALL_JOB_CHIPS)
    val hidden = jobs.size - shown.size

    div("small-jobs") {
        span("small-jobs__label") { +"Small jobs · tick off as you go" }
        div("small-jobs__chips") {
            shown.forEach { smallJobChip(worldId, projectId, it) }
            if (hidden > 0) {
                details("small-jobs__rest") {
                    summary {
                        span("btn btn--ghost btn--sm") { +"$hidden more" }
                    }
                    div("small-jobs__chips") {
                        jobs.drop(SMALL_JOB_CHIPS).forEach { smallJobChip(worldId, projectId, it) }
                    }
                }
            }
        }
    }
}


/**
 * "Worth a farm" — the farm-scale demand and what to build for it, as one list.
 *
 * MCO-401 shipped the quantities; MCO-294 added the designs that cover them. They were briefly
 * two sections and that was wrong: on a real plan the bank answered ten of ten lines, so the
 * second section reprinted the first one's numbers under different headings. A design and the
 * demand it covers are one fact, so they are one row.
 *
 * The order is the message, as it was before: most work removed first, and the top line is
 * where to start. Designs lead because a line you can answer by importing something is a
 * cheaper decision than a line you cannot.
 *
 * The tail is the point of the section as much as the head. A farm-scale material with no
 * design is not a gap in the feature — it is the honest answer that you will be farming this
 * one yourself, and it is where the bank is worth growing. It keeps the plain quantity-and-name
 * shape the whole roll-up used to have.
 *
 * Coverage runs deeper than the roll-up's own lines: the roll-up is RAW_GATHER leaves, and a
 * design that produces iron ingots removes the *ore* below them. That ore is a roll-up line, so
 * it appears under the design that removes it rather than orphaned in the tail claiming nobody
 * can help with it.
 */
private fun FlowContent.farmScaleSection(
    demands: List<FarmScaleDemand>,
    suggestions: List<FarmSuggestion>,
    threshold: Int,
    worldId: Int,
    projectId: Int,
    /** Gates both routes into world-level judgement here: the threshold link and dismissal. */
    isWorldAdmin: Boolean,
    dismissals: List<FarmDismissal> = emptyList(),
    /** The dismissed lines this plan would otherwise be showing, for the ignored fold. */
    suppressed: List<FarmScaleDemand> = emptyList(),
) {
    // The dismissals keep the section alive on their own: a panel with nothing left to say is
    // still the only place the decision can be taken back.
    if (demands.isEmpty() && suggestions.isEmpty() && dismissals.isEmpty()) return

    val answered = suggestions.flatMapTo(mutableSetOf()) { it.itemIds }
    val unanswered = demands.filter { it.itemId !in answered }

    div("plan-farm-scale") {
        id = "plan-farm-scale"
        span("section-label") { +"Worth a farm" }
        p("plan-farm-scale__lead") {
            if (demands.isEmpty() && suggestions.isEmpty()) {
                +"Nothing in this plan is above "
            } else {
                +"${demands.size} raw ${if (demands.size == 1) "material needs" else "materials need"} more than "
            }
            // The number is the judgement this whole list rests on, so it is the thing to edit:
            // disagreeing with the list means disagreeing with the threshold. Admin-only, matching
            // how every other route to world settings is gated (settingsHref in Navigation.kt) —
            // a link that 403s is worse than no link.
            if (isWorldAdmin) {
                a(classes = "plan-farm-scale__threshold") {
                    href = "/worlds/$worldId/settings"
                    title = "Change this world's farm-scale threshold"
                    +"%,d".format(threshold)
                }
            } else {
                +"%,d".format(threshold)
            }
            when {
                demands.isEmpty() && suggestions.isEmpty() -> +"."
                suggestions.isEmpty() -> +" — each is a candidate for its own farm project."
                else -> {
                    val covered = demands.size - unanswered.size
                    +" — your designs cover $covered of them."
                }
            }
        }

        // One form around every design so a batch is one submit (MCO-459). A plain POST, not
        // HTMX: the wizard it opens is a sequence of full pages, and swapping a fragment in
        // would leave the plan underneath claiming demand the first step is about to answer.
        //
        // Guarded on there being designs at all: this section also renders for a plan whose
        // farm-scale demand nothing in the bank answers, and an empty form with a live submit
        // is a button that does nothing.
        if (suggestions.isNotEmpty()) form(classes = "plan-farm-scale__batch") {
            method = FormMethod.post
            action = "/worlds/$worldId/projects/$projectId/farm-suggestions/import"

            // Grouped, not listed (MCO-483): designs covering the same demand are a choice.
            FarmSuggestionChoices.of(suggestions).forEach { choice ->
                designChoice(choice, worldId, projectId, isWorldAdmin)
            }

            div("plan-farm-scale__batch-actions") {
                button(classes = "btn btn--sm btn--primary plan-farm-scale__batch-submit") {
                    type = ButtonType.submit
                    +"Review selected designs"
                }
                span("plan-farm-scale__batch-hint") {
                    +"Reviewed one at a time, then back to this plan."
                }
            }
        }

        if (unanswered.isNotEmpty()) {
            div("plan-farm-scale__unanswered") {
                // The label separates answered from unanswered. With nothing answered there is
                // nothing to separate, and the section is exactly the roll-up MCO-401 shipped —
                // so it says nothing rather than heading a list that is the whole list.
                if (suggestions.isNotEmpty()) {
                    span("plan-farm-scale__group-label") { +"No design yet" }
                }
                div("plan-farm-scale__list") {
                    unanswered.forEach { demand ->
                        farmScaleDemandLine(
                            worldId, projectId, demand.itemId, demand.itemName, demand.quantity,
                            canDismiss = isWorldAdmin,
                        )
                    }
                }
            }
        }

        dismissedFarmDemands(worldId, projectId, dismissals, suppressed, canRestore = isWorldAdmin)
    }
}

/**
 * One line of the roll-up: how much, of what, and the way out of being asked about it (MCO-407).
 *
 * Every line in the panel is rendered by this — the ones with a design under them and the ones
 * without — because "I have decided against this" applies to both, and a control that only some
 * lines carry reads as a control that means something different on each.
 */
private fun FlowContent.farmScaleDemandLine(
    worldId: Int,
    projectId: Int,
    itemId: String,
    itemName: String,
    quantity: Long,
    rateLabel: String? = null,
    canDismiss: Boolean = false,
) {
    div("plan-farm-scale__item") {
        span("plan-farm-scale__quantity") { +"%,d".format(quantity) }
        span("plan-farm-scale__name") { +itemName }
        rateLabel?.let { label -> span("plan-farm-scale__rate") { +label } }
        if (canDismiss) {
            button(classes = "btn btn--ghost btn--sm plan-farm-scale__dismiss") {
                // type=button, because half of these lines sit inside the batch import form and
                // a default submit would open the review wizard instead.
                type = ButtonType.button
                hxPost(farmDismissalHref(worldId, projectId, itemId))
                // The enclosing form's ticked designs are not part of this decision. htmx would
                // include them by default for a POST from inside a form.
                attributes["hx-params"] = "none"
                // The whole plan, not this line: a dismissal takes a roll-up line, possibly a
                // design row with it, and a badge on a work row several sections down — one
                // decision, so one swap. Matches the id gatheringPlannerFragment renders.
                hxTarget("#project-content")
                hxSwap("outerHTML")
                title = "Stop suggesting a farm for $itemName in this world"
                +"Dismiss"
            }
        }
    }
}

/**
 * The dismissed items, folded away but never gone (MCO-407).
 *
 * A dismissal is permanent until it is taken back, which is only defensible if taking it back is
 * findable — so this lists **every** dismissal the world holds, not only the ones this plan
 * would be showing. The one it shows against each is today's demand beside the demand it was
 * dismissed at: an item whose demand has since multiplied says so here, where the undo is,
 * rather than reappearing in the roll-up on its own and needing to be dismissed twice.
 */
private fun FlowContent.dismissedFarmDemands(
    worldId: Int,
    projectId: Int,
    dismissals: List<FarmDismissal>,
    suppressed: List<FarmScaleDemand>,
    canRestore: Boolean,
) {
    if (dismissals.isEmpty()) return
    val stillDemanded = suppressed.associate { it.itemId to it.quantity }

    details("plan-farm-scale__dismissed") {
        summary {
            span("btn btn--ghost btn--sm plan-farm-scale__dismissed-toggle") {
                span("plan-farm-scale__dismissed-toggle--closed") {
                    +"${dismissals.size} dismissed ▾"
                }
                span("plan-farm-scale__dismissed-toggle--open") { +"Hide dismissed ▴" }
            }
        }
        div("plan-farm-scale__dismissed-list") {
            dismissals.forEach { dismissal ->
                div("plan-farm-scale__dismissed-item") {
                    span("plan-farm-scale__dismissed-name") { +dismissal.itemName }
                    dismissalNote(dismissal, stillDemanded[dismissal.itemId])
                    if (canRestore) {
                        button(classes = "btn btn--ghost btn--sm plan-farm-scale__restore") {
                            type = ButtonType.button
                            hxDelete(farmDismissalHref(worldId, projectId, dismissal.itemId))
                            attributes["hx-params"] = "none"
                            hxTarget("#project-content")
                            hxSwap("outerHTML")
                            title = "Suggest a farm for ${dismissal.itemName} again"
                            +"Restore"
                        }
                    }
                }
            }
        }
    }
}

/** How much this world still wants of a dismissed item, against how much it wanted then. */
private fun FlowContent.dismissalNote(dismissal: FarmDismissal, currentQuantity: Long?) {
    val at = dismissal.quantityAtDismissal
    when {
        currentQuantity == null ->
            span("plan-farm-scale__dismissed-note") { +"not farm-scale in this plan" }

        currentQuantity == at ->
            span("plan-farm-scale__dismissed-note") { +"%,d here".format(currentQuantity) }

        // Ten times the demand is a different decision from the one that was made; two times is
        // already worth a glance. Marked rather than acted on — nothing here un-dismisses itself.
        at > 0 && currentQuantity >= at * 2 ->
            span("plan-farm-scale__dismissed-note plan-farm-scale__dismissed-note--grown") {
                +"%,d here now, dismissed at %,d".format(currentQuantity, at)
            }

        else ->
            span("plan-farm-scale__dismissed-note") {
                +"%,d here, dismissed at %,d".format(currentQuantity, at)
            }
    }
}

/** The dismissal endpoint for one item — POST to dismiss, DELETE to take it back. */
private fun farmDismissalHref(worldId: Int, projectId: Int, itemId: String): String =
    "/worlds/$worldId/projects/$projectId/farm-suggestions/dismissals/" +
        URLEncoder.encode(itemId, StandardCharsets.UTF_8).replace("+", "%20")

/**
 * One demand, the design to build for it, and the designs that would do instead (MCO-294, MCO-483).
 *
 * **One row per design, not per item.** A farm makes several things: the bank's stick producer
 * is the Witch Hut Farm, already the answer for 63,213 redstone. A row per item would name that
 * one design three times and invite building a witch hut "for sticks". See
 * [app.mcorg.pipeline.resources.FarmSuggestions] for the matching rules and for why coverage
 * deliberately under-claims.
 *
 * **And one row per demand, not per design.** Two ice farms covering the same 20,611 Ice were two
 * peer rows with two checkboxes under "Review selected designs", which reads as "build both".
 * They are one job with two answers, so the recommendation takes the row and the rest sit behind
 * a fold that says what they are. [app.mcorg.pipeline.resources.FarmSuggestionChoices] owns the
 * grouping key and the ranking; this only renders the reason it produced.
 *
 * The hours figure is the one number that changes a decision. Most designs cover their demand in
 * minutes — it is the rare multi-hour line that says a farm is a project rather than an
 * afternoon — so the rate shows where it is known and is quietly absent where the author never
 * measured it (a null rate is meaningful here, not missing data).
 *
 * The action is the review screen, not a direct create: import decides what to gather and
 * whether the farm already exists (MCO-457), and neither is this list's to answer for the user.
 */
private fun FlowContent.designChoice(
    choice: FarmSuggestionChoice,
    worldId: Int,
    projectId: Int,
    isWorldAdmin: Boolean,
) {
    val recommended = choice.recommended

    div("plan-farm-scale__design") {
        // Scopes the one-of-N rule to this demand. The value is never used as a selector — see
        // farm-suggestions.js, which walks up to the nearest wrapper carrying the attribute.
        attributes["data-farm-choice"] = choice.key

        div("plan-farm-scale__design-head") {
            designSelect(recommended)
            a(classes = "plan-farm-scale__design-link") {
                href = Link.Ideas.single(recommended.ideaId)
                title = "Open ${recommended.ideaName}"
                +"View design"
            }
        }

        // Why this one leads, in the row rather than in the ranking code. A 1.4% rate difference
        // decided the ice farms; unstated, that is an arbitrary answer dressed as advice.
        recommendationNote(choice)?.let { note -> p("plan-farm-scale__why") { +note } }

        // Rendered always, revealed by CSS when anything in this group is ticked (:has). Ticking
        // a design has to *settle* the demand visibly, or the alternatives keep inviting a
        // second tick for a job that is already answered.
        span("plan-farm-scale__chosen") { +"Chosen — this demand is settled." }

        div("plan-farm-scale__list") {
            recommended.produces.forEach { covered ->
                farmScaleDemandLine(
                    worldId,
                    projectId,
                    covered.itemId,
                    covered.itemName,
                    covered.quantity,
                    rateLabel = covered.hoursToCover?.let { hoursOfRunning(it) },
                    // Dismissible like any other line: "I am buying this from a villager" is an
                    // answer to a demand that happens to have a design, too. Dismissing the last
                    // line a design covers takes the design with it, which is the point.
                    canDismiss = isWorldAdmin,
                )
            }
        }

        if (recommended.alsoRemoves.isNotEmpty()) {
            p("plan-farm-scale__knock-on") {
                +"Also removes "
                +recommended.alsoRemoves.joinToString(", ") {
                    "${"%,d".format(it.quantity)} ${it.itemName}"
                }
                +" — work that only exists to feed the above."
            }
        }

        if (choice.alternatives.isNotEmpty()) {
            // Same fold shape as "Needs attention" (MCO-400) and the folded resource tail
            // (MCO-478): a `details` whose summary is a ghost button that says what it will do.
            details("plan-farm-scale__alternatives") {
                summary {
                    span("btn btn--ghost btn--sm plan-farm-scale__alternatives-toggle") {
                        span("plan-farm-scale__alternatives-toggle--closed") {
                            +"${choice.alternatives.size} other ${designWord(choice.alternatives.size)} ${coverVerb(choice.alternatives.size)} this ▾"
                        }
                        span("plan-farm-scale__alternatives-toggle--open") {
                            +"Hide the other ${designWord(choice.alternatives.size)} ▴"
                        }
                    }
                }
                div("plan-farm-scale__alternative-list") {
                    choice.alternatives.forEach { alternative -> alternativeDesign(alternative) }
                }
            }
        }
    }
}

/**
 * One alternative inside a choice: the name, its own coverage time, and the link.
 *
 * It does **not** reprint the quantities. They are identical to the recommendation's by
 * construction — that is what put these designs in one group — and printing them again is the
 * duplication MCO-483 is removing, one level further in.
 */
private fun FlowContent.alternativeDesign(suggestion: FarmSuggestion) {
    div("plan-farm-scale__alternative") {
        designSelect(suggestion)
        suggestion.coverageHours?.let { hours ->
            span("plan-farm-scale__rate") { +hoursOfRunning(hours) }
        }
        a(classes = "plan-farm-scale__design-link") {
            href = Link.Ideas.single(suggestion.ideaId)
            title = "Open ${suggestion.ideaName}"
            +"View design"
        }
    }
}

/**
 * The checkbox and the name, as one target.
 *
 * The checkbox replaced the per-row "Import into this world" link rather than sitting beside it
 * (MCO-459). Two doors to the same place on an already dense row is a choice nobody wants to
 * make; selecting a design and submitting is the old flow exactly, one click longer, and every
 * other route into the review screen still exists from the idea page itself.
 *
 * Still a checkbox and not a radio, though a choice is one-of-N: the batch form carries every
 * choice on the page, and one radio group per choice would need a field name per group — a wire
 * format change to [SELECTED_DESIGN_FIELD] for a rule the same form can hold with a data
 * attribute. Exclusivity within a group is enforced in `farm-suggestions.js`, and the fold means
 * the second tick is not even reachable without opening it.
 */
private fun FlowContent.designSelect(suggestion: FarmSuggestion) {
    label("plan-farm-scale__select") {
        htmlFor = "design-select-${suggestion.ideaId}"
        checkBoxInput(classes = "plan-farm-scale__select-box") {
            id = "design-select-${suggestion.ideaId}"
            name = SELECTED_DESIGN_FIELD
            value = suggestion.ideaId.toString()
        }
        span("plan-farm-scale__design-name") { +suggestion.ideaName }
    }
}

/**
 * What ranked the designs in this choice, as a sentence — null when there is nothing to explain.
 *
 * Every branch names the evidence rather than asserting a winner: the runner-up's time is printed
 * beside the leader's precisely because 5.7h against 5.8h is a coin toss and should read as one.
 */
private fun recommendationNote(choice: FarmSuggestionChoice): String? {
    val total = choice.designs.size
    return when (val reason = choice.reason) {
        is RecommendationReason.Sole -> null
        is RecommendationReason.Fastest -> {
            val mine = runningTime(reason.hours)
            val next = runningTime(reason.runnerUpHours)
            // The ice farms differ by 1.4%, which at this precision is no difference at all:
            // "~17 min against ~17 min for the next" states a gap the reader cannot see and
            // reads as a bug. Say that it is a hair rather than printing the same figure twice.
            if (mine == next) "Fastest of $total designs that cover this, though only just — both cover it in about $mine."
            else "Fastest of $total designs that cover this — $mine against $next for the next."
        }
        is RecommendationReason.OnlyMeasured ->
            "The only one of $total designs here with a measured rate — ${runningTime(reason.hours)}."
        is RecommendationReason.NoFasterOption -> when (val hours = reason.hours) {
            null -> "$total designs cover this, none with a measured rate — listed by name."
            else -> "$total designs cover this in the same ${runningTime(hours)} — listed by name."
        }
    }
}

private fun designWord(count: Int): String = if (count == 1) "design" else "designs"

private fun coverVerb(count: Int): String = if (count == 1) "covers" else "cover"

/**
 * How long the farm has to run, in the unit a player would say it in.
 *
 * Sub-hour figures are the common case and "0.1 hours" is not how anyone thinks about ten
 * minutes; past a day, minutes and hours both stop being the point.
 */
private fun hoursOfRunning(hours: Double): String = "${runningTime(hours)} running"

/**
 * The same figure without the "running" suffix, for the sentence that compares two of them
 * (MCO-483) — "~5.7 h against ~5.8 h running for the next" reads as one farm, not two.
 */
private fun runningTime(hours: Double): String = when {
    hours < 1.0 -> "~${kotlin.math.max(1, kotlin.math.round(hours * 60).toInt())} min"
    hours < 24.0 -> "~%.1f h".format(hours)
    else -> "~%.1f days".format(hours / 24)
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
/**
 * The farms this project waits on (MCO-299, reframed by MCO-461).
 *
 * This shipped as a promise — "63,213 Redstone Dust will come from Witch Hut Farm once it is
 * running". Every word true, but it read as a courtesy note rather than an ordering fact, so
 * nothing on the page contradicted the suggestion list offering to import that same farm again.
 *
 * It now leads with the relationship — *Prerequisite* — and keeps the promise as the
 * explanation underneath it. Same farm, same numbers, one claim instead of two: the list comes
 * from [prerequisiteFarmsFor], which reads the roadmap's own edges, so this page and the
 * roadmap cannot disagree about what blocks what.
 */
private fun FlowContent.pendingFarmNotice(worldId: Int, pendingFarms: List<PendingFarmSupply>) {
    if (pendingFarms.isEmpty()) return

    div("plan-pending-farms") {
        id = "plan-pending-farms"
        span("section-label") { +"Prerequisites" }
        pendingFarms.forEach { farm ->
            p("plan-pending-farms__line") {
                a(classes = "plan-pending-farms__project") {
                    href = "/worlds/$worldId/projects/${farm.projectId}"
                    +farm.projectName
                }
                +" comes first — it makes "
                +itemsPhrase(farm.items)
                +" this build needs. Gather "
                +(if (farm.items.size == 1) "it" else "them")
                +" by hand until it is running."
            }
        }
    }
}

/** "32 Iron Ingot", "32 Iron Ingot and 12 Gold Ingot", "32 Iron Ingot, 12 Gold Ingot and 4 Diamond". */
private fun itemsPhrase(items: List<PendingFarmItem>): String {
    // Separated, like every other quantity on this page. Unformatted was survivable while
    // this read "32 Iron Ingot"; MCO-461 made it a prerequisite line carrying farm-scale
    // numbers, and "2400" beside the roll-up's "2,400" reads as a different number.
    val parts = items.map { "%,d %s".format(it.quantity, it.itemName) }
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


/** Renders the "Feeds 24 Birch Door · 40 Chest" reverse-provenance line, when present. */
internal fun FlowContent.feedsLine(label: FeedsLabel?) {
    if (label == null) return
    div("resource-row__feeds") {
        label.title?.let { attributes["title"] = it }
        +label.text
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
private fun FlowContent.needsAttentionList(
    project: Project,
    activities: List<Activity>,
    versionGaps: Set<String> = emptySet(),
) {
    val blocked = activities.filter { it.status == PlanNodeStatus.BLOCKED }
    val questions = attentionQuestions(activities)

    div("resource-list") {
        blocked.forEach { blockedActivityRow(project.worldId, project.id, it, it.item.id in versionGaps) }
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
            // MCO-507: "answer the remaining N with the recommended pick", loaded on demand.
            //
            // Lazy rather than rendered inline because the picks need the item-source graph to
            // rank, and the ranking that this control *shows* must be the same computation that
            // the POST then *applies* — a preview built here from a threaded-in graph would be a
            // second implementation of "recommended", free to drift from the one that writes.
            // One request, on a section that only exists when there is a tail to fold.
            //
            // Empty response when there is nothing to offer (fewer than two folded questions, or
            // no graph), so the slot just stays empty.
            div("plan-attention__bulk-slot") {
                id = BULK_ANSWER_SLOT_ID
                attributes["hx-get"] =
                    "/worlds/${project.worldId}/projects/${project.id}/plan/attention/bulk"
                attributes["hx-trigger"] = "load"
                attributes["hx-target"] = "this"
                attributes["hx-swap"] = "innerHTML"
            }
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
 * The questions "Needs attention" folds away — the small tail, and the exact set MCO-507's bulk
 * action is allowed to answer.
 *
 * Exposed because the action must cover precisely what the section hid and nothing else: offering
 * to answer a *lead* question would be offering to answer the ones worth reading. One definition,
 * read by the renderer below and by `PlanAttentionBulkPipeline` on the server.
 */
internal fun foldedAttentionQuestions(activities: List<Activity>): List<Activity> {
    val questions = attentionQuestions(activities)
    if (questions.isEmpty()) return emptyList()
    return questions.drop(leadingQuestionCount(questions))
}

/** The open questions in the order the section asks them: biggest decision first. */
private fun attentionQuestions(activities: List<Activity>): List<Activity> =
    activities
        .filter { it.status == PlanNodeStatus.OPEN_TAG }
        .sortedWith(compareByDescending<Activity> { it.quantity }.thenBy { it.item.name })

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
/**
 * The line above the questions.
 *
 * It carries the "provisional" warning that used to live in the Next up widget (MCO-482, moved
 * by MCO-504). Next up is now suppressed entirely while a question is open, so this is the only
 * place that can say why the plan below is not yet final — and it is the right place, because it
 * is where the questions are.
 */
private fun attentionLead(questions: List<Activity>, leadCount: Int): String {
    val noun = if (questions.size == 1) "question" else "questions"
    val provisional = if (questions.size == 1) {
        "The plan below is provisional until it is answered."
    } else {
        "The plan below is provisional until they are answered."
    }

    if (leadCount >= questions.size) {
        return "${questions.size} $noun to answer. $provisional"
    }

    val total = questions.sumOf { it.quantity }
    if (total <= 0) return "${questions.size} $noun to answer, largest first. $provisional"

    val covered = questions.take(leadCount).sumOf { it.quantity }
    val percent = (covered * 100.0 / total).roundToInt()
    val these = if (leadCount == 1) "the first decides" else "these $leadCount decide"
    return "${questions.size} $noun to answer — $these $percent% of the material behind them. " +
        provisional
}

/**
 * What a variant question is asking, in one sentence.
 *
 * One constant, because the question must read identically wherever it is asked. It is asked in
 * one place today (MCO-504 withdrew the Next up copy), and a constant is what keeps a second
 * surface from inventing its own wording the next time one is added.
 *
 * "Recipes" is the load-bearing word: these blocks are interchangeable *to a crafting recipe*,
 * which is not deducible from a list of block names. Neither "variant" nor "open tag" appears —
 * the reader is choosing a material, not a variant, and "open tag" was ours.
 */
private const val VARIANT_QUESTION = "Which should the plan use in recipes?"

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
            div("plan-attention__line") {
                span("plan-attention__quantity") { +"%,d".format(activity.quantity) }
                +" "
                span { +activity.item.name }
            }
            // And then the question itself (MCO-504). MCO-489 made the label name the options
            // — "Red Sand or Sand" rather than "Smelts To Glass" — but the row still never said
            // what picking one *does*, and ended in "(open tag)", which is a PlanNodeStatus enum
            // name that had leaked from the engine onto the page.
            div("plan-attention__question") { +VARIANT_QUESTION }
        }
        // Resolve inline: drops the tag-member picker below this row; a pick re-renders the
        // List lens (origin=list) so the resolved tag leaves "Needs attention".
        button(classes = "btn btn--primary btn--sm") {
            type = ButtonType.button
            attributes["hx-get"] =
                "/worlds/$worldId/projects/$projectId/plan/chain/$encodedItemId/sources?node=$encodedItemId&origin=list"
            attributes["hx-target"] = "#$pickerSlotId"
            attributes["hx-swap"] = "innerHTML"
            +"Choose"
        }
        // ⇄ still opens the full drill to explore/re-pin the whole chain.
        drillButton(worldId, projectId, encodedItemId)
    }
    div("chain-node__picker") { id = pickerSlotId }
}

/**
 * BLOCKED row: warning callout.
 *
 * [missingFromVersion] separates the two reasons a row blocks, which want different actions of the
 * reader. "No feasible source" is a fact about this Minecraft version — a command block is not
 * obtainable and no amount of editing changes that. An id the version's catalog does not contain
 * at all is a fact about *the row*: it survived a version switch that removed the item, and the fix
 * is to swap it for whatever replaced it. Saying "no feasible source found" for the second case
 * sends the reader looking for a recipe that was never the problem.
 */
private fun FlowContent.blockedActivityRow(
    worldId: Int,
    projectId: Int,
    activity: Activity,
    missingFromVersion: Boolean = false,
) {
    val encodedItemId = URLEncoder.encode(activity.item.id, StandardCharsets.UTF_8)
    div("callout callout--warning") {
        id = "plan-activity-${activity.item.id.replace(":", "-")}"
        span("callout__icon") { +"!" }
        div("callout__body") {
            span { +"Blocked: " }
            +activity.item.name
            if (missingFromVersion) {
                +" — not in this world's Minecraft version"
            } else {
                +" — no feasible source found"
            }
        }
        drillButton(worldId, projectId, encodedItemId)
    }
}

/**
 * RESOLVED / RAW_GATHER: counter row posting to the (projectId, itemId) progress endpoint.
 * Mirrors the structure of resourceRow but targets the plan progress endpoint.
 * [progressMap] carries persisted progress for all items in the project (including derived ones).
 */

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

internal fun groupLabel(group: ActivityGroup): String = when (group) {
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
                        +"Schematic files"
                        span("required-indicator") { +"*" }
                    }
                    input(classes = "form-control") {
                        id = "resource-schematic-file"
                        type = InputType.file
                        name = "schematicFile"
                        accept = ".litematic"
                        required = true
                        // A build that spans dimensions is several files (MCO-414); replacing the
                        // list from only one of them would drop the rest of the build.
                        multiple = true
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
    progressMap: Map<String, Int> = emptyMap(),
    pendingFarms: List<PendingFarmSupply> = emptyList(),
    farmScaleThreshold: Int = World.DEFAULT_FARM_SCALE_THRESHOLD,
    farmSuggestions: List<FarmSuggestion> = emptyList(),
    /** Stored ids this world's Minecraft version has no catalog entry for (MCO-157). */
    versionGaps: Set<String> = emptySet(),
    isWorldAdmin: Boolean = false,
    /** Farm-scale demand this world has decided against (MCO-407). */
    farmDismissals: List<FarmDismissal> = emptyList(),
): String = createHTML().div {
    id = "project-content"
    gatheringPlannerContent(
        project, resources, tasks, plan, progressMap, pendingFarms, farmScaleThreshold, farmSuggestions, versionGaps, isWorldAdmin,
        farmDismissals,
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
