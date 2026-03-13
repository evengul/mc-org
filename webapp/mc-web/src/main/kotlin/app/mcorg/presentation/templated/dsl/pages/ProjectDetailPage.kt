package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.domain.model.task.ActionTask
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.dsl.BadgeStatus
import app.mcorg.presentation.templated.dsl.BreadcrumbBuilder
import app.mcorg.presentation.templated.dsl.addTaskInline
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.pageShell
import app.mcorg.presentation.templated.dsl.progressBar
import app.mcorg.presentation.templated.dsl.resourceRow
import app.mcorg.presentation.templated.dsl.resourceSearch
import app.mcorg.presentation.templated.dsl.statusBadge
import app.mcorg.presentation.templated.dsl.taskList
import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun projectDetailPage(
    user: TokenProfile,
    project: Project,
    resources: List<ResourceGatheringItem>,
    tasks: List<ActionTask>,
    view: String = "execute"
): String = pageShell(
    pageTitle = "MC-ORG — ${project.name}",
    user = user,
    stylesheets = listOf(
        "/static/styles/components/btn.css",
        "/static/styles/components/toggle.css",
        "/static/styles/components/badge.css",
        "/static/styles/components/progress.css",
        "/static/styles/components/resource-row.css",
        "/static/styles/components/task-list.css",
        "/static/styles/components/resource-search.css",
        "/static/styles/pages/project-detail.css",
    ),
    scripts = listOf("/static/scripts/resource-search.js")
) {
    appHeader(
        worldId = project.worldId,
        projectId = project.id,
        user = user,
        breadcrumbBlock = {
            link("Worlds", "/worlds")
                .link(project.name, "/worlds/${project.worldId}/projects")
                .current(project.name)
        }
    )
    // Mobile header — separate toggle with distinct id
    div("project-detail__mobile-header") {
        a(classes = "project-detail__back-btn") {
            href = "/worlds/${project.worldId}/projects"
            +"\u2190"
        }
        p("project-detail__mobile-name") { +project.name }
        projectDetailToggleButtons(project.worldId, project.id, view, "project-detail-toggle-mobile")
    }
    main {
        container {
            // Desktop header
            div("project-detail__header") {
                div("project-detail__header-left") {
                    h1("project-detail__name") { +project.name }
                    div("project-detail__meta") {
                        statusBadge(project.stage.toBadgeStatus())
                        project.location?.let { loc ->
                            span("project-detail__location") {
                                +"X: ${loc.x}, Z: ${loc.z}"
                            }
                        }
                    }
                    val filteredResources = resources.filter { it.required > 0 }
                    val totalRequired = filteredResources.sumOf { it.required }
                    val totalCollected = filteredResources.sumOf { it.collected }
                    if (totalRequired > 0) {
                        div("project-detail__overall-progress") {
                            p("project-detail__overall-progress-label") {
                                +"$totalCollected / $totalRequired gathered"
                            }
                            div {
                                id = "overall-progress"
                                progressBar(totalCollected, totalRequired)
                            }
                        }
                    }
                }
                projectDetailToggleButtons(project.worldId, project.id, view, "project-detail-toggle")
            }

            div {
                id = "project-content"
                if (view == "plan") {
                    planViewStub()
                } else {
                    executeViewContent(project, resources, tasks)
                }
            }
        }
    }
}

fun FlowContent.projectDetailToggleButtons(worldId: Int, projectId: Int, active: String, toggleId: String) {
    div("toggle") {
        id = toggleId
        button {
            classes = buildSet {
                add("toggle__btn")
                if (active == "plan") add("toggle__btn--active")
            }
            hxGet("/worlds/$worldId/projects/$projectId/detail-content?view=plan")
            hxTarget("#project-content")
            hxSwap("innerHTML")
            +"PLAN"
        }
        button {
            classes = buildSet {
                add("toggle__btn")
                if (active == "execute") add("toggle__btn--active")
            }
            hxGet("/worlds/$worldId/projects/$projectId/detail-content?view=execute")
            hxTarget("#project-content")
            hxSwap("innerHTML")
            +"EXEC"
        }
    }
}

fun FlowContent.executeViewContent(
    project: Project,
    resources: List<ResourceGatheringItem>,
    tasks: List<ActionTask>
) {
    val filteredResources = resources.filter { it.required > 0 }

    // Resources section
    div("project-detail__section") {
        div("project-detail__section-header") {
            span("project-detail__section-title section-label") { +"Resources to gather" }
        }
        div("resource-list") {
            id = "resource-list-container"
            if (filteredResources.isEmpty()) {
                div("resource-list__empty") {
                    +"No resources defined yet."
                }
            } else {
                resourceSearch()
                div {
                    id = "resource-list"
                    filteredResources.forEach { item ->
                        resourceRow(
                            id = item.id,
                            worldId = project.worldId,
                            projectId = project.id,
                            itemName = item.name,
                            current = item.collected,
                            required = item.required,
                            source = item.solvedByProject?.second
                        )
                    }
                    div("resource-list__no-match") { +"No resources match your search." }
                }
            }
            if (tasks.isNotEmpty()) {
                val done = tasks.count { it.completed }
                a(classes = "resource-list__tasks-anchor") {
                    href = "#task-section"
                    +"↓ Tasks — $done of ${tasks.size} completed"
                }
            }
        }
    }

    // Tasks section
    div("project-detail__section") {
        id = "task-section"
        div("project-detail__section-header") {
            span("project-detail__section-title section-label") { +"Tasks" }
        }
        div("task-section") {
            // OOB target for CompleteActionTask response
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

fun FlowContent.planViewStub() {
    div("plan-view-stub") {
        h2("plan-view-stub__title") { +"Plan view" }
        p("plan-view-stub__body") { +"Coming soon — use the execute view to track your progress." }
    }
}

private fun ProjectStage.toBadgeStatus(): BadgeStatus = when (this) {
    ProjectStage.IDEA, ProjectStage.DESIGN, ProjectStage.PLANNING -> BadgeStatus.NOT_STARTED
    ProjectStage.RESOURCE_GATHERING, ProjectStage.BUILDING, ProjectStage.TESTING -> BadgeStatus.IN_PROGRESS
    ProjectStage.COMPLETED -> BadgeStatus.DONE
}

// Fragment for detail-content endpoint response (inner content of #project-content)
fun executeViewFragment(project: Project, resources: List<ResourceGatheringItem>, tasks: List<ActionTask>): String =
    createHTML().div {
        executeViewContent(project, resources, tasks)
    }

fun planViewFragment(): String = createHTML().div {
    planViewStub()
}

// OOB fragments to update both toggle instances when switching views
fun toggleOobFragments(worldId: Int, projectId: Int, active: String): String {
    return buildToggleOob(worldId, projectId, active, "project-detail-toggle") +
           buildToggleOob(worldId, projectId, active, "project-detail-toggle-mobile")
}

// OOB fragment to update #project-progress after task create/complete
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

private fun buildToggleOob(worldId: Int, projectId: Int, active: String, toggleId: String): String =
    createHTML().div("toggle") {
        id = toggleId
        hxOutOfBands("outerHTML:#$toggleId")
        button {
            classes = buildSet {
                add("toggle__btn")
                if (active == "plan") add("toggle__btn--active")
            }
            hxGet("/worlds/$worldId/projects/$projectId/detail-content?view=plan")
            hxTarget("#project-content")
            hxSwap("innerHTML")
            +"PLAN"
        }
        button {
            classes = buildSet {
                add("toggle__btn")
                if (active == "execute") add("toggle__btn--active")
            }
            hxGet("/worlds/$worldId/projects/$projectId/detail-content?view=execute")
            hxTarget("#project-content")
            hxSwap("innerHTML")
            +"EXEC"
        }
    }
