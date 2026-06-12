package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.project.ProjectListItem
import app.mcorg.domain.model.project.ProjectState
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.summary

/**
 * Field Log — state-sorted sections of the world's project list (execute view).
 * Active projects get full rows; pending/paused collapse to chips; terminal
 * states (done/cancelled/archived) share a single collapsed shelf.
 */

fun FlowContent.fieldLogSections(worldId: Int, projects: List<ProjectListItem>) {
    val byState = projects.groupBy { it.state }
    val active = (byState[ProjectState.ACTIVE] ?: emptyList())
        .sortedByDescending { it.progressFraction() }
    val pending = byState[ProjectState.PENDING] ?: emptyList()
    val paused = byState[ProjectState.PAUSED] ?: emptyList()
    val done = byState[ProjectState.DONE] ?: emptyList()
    val cancelled = byState[ProjectState.CANCELLED] ?: emptyList()
    val archived = byState[ProjectState.ARCHIVED] ?: emptyList()

    if (active.isNotEmpty()) {
        div("fl-section") {
            attributes["id"] = "fl-active-section"
            span("section-label") { +"Active · ${active.size}" }
            div("fl-row-list") {
                attributes["id"] = "fl-active-list"
                active.forEach { fieldLogRow(worldId, it) }
            }
        }
    }

    if (pending.isNotEmpty()) {
        fieldLogChipSection("fl-pending-section", "Pending · ${pending.size}", worldId, pending, dimmed = false)
    }

    if (paused.isNotEmpty()) {
        fieldLogChipSection("fl-paused-section", "Paused · ${paused.size}", worldId, paused, dimmed = true)
    }

    if (done.isNotEmpty() || cancelled.isNotEmpty() || archived.isNotEmpty()) {
        fieldLogDoneShelf(worldId, done, cancelled, archived)
    }
}

private fun ProjectListItem.progressFraction(): Double =
    if (resourcesRequired > 0) resourcesGathered.toDouble() / resourcesRequired else 0.0

fun FlowContent.fieldLogRow(worldId: Int, project: ProjectListItem) {
    div("fl-row") {
        attributes["id"] = "fl-row-${project.id}"
        div("fl-row__head") {
            div("fl-row__title") {
                a(classes = "fl-row__name") {
                    href = "/worlds/$worldId/projects/${project.id}"
                    +project.name
                }
                projectStateBadge(project.id, project.state)
                if (project.itemCount > 0) {
                    span("fl-row__sub") { +"${project.itemCount} item${if (project.itemCount == 1) "" else "s"}" }
                }
            }
            div("fl-row__stats") {
                if (project.resourcesRequired > 0) {
                    span("fl-row__count") {
                        +"${formatItemCount(project.resourcesGathered)} / ${formatItemCount(project.resourcesRequired)}"
                    }
                    div("fl-row__progress") {
                        progressBar(project.resourcesGathered, project.resourcesRequired)
                    }
                } else if (project.tasksTotal > 0) {
                    span("fl-row__count") { +"${project.tasksDone} / ${project.tasksTotal} tasks" }
                    div("fl-row__progress") {
                        progressBar(project.tasksDone, project.tasksTotal)
                    }
                }
            }
        }
    }
}

private fun FlowContent.fieldLogChipSection(
    id: String,
    label: String,
    worldId: Int,
    projects: List<ProjectListItem>,
    dimmed: Boolean,
) {
    div("fl-section") {
        attributes["id"] = id
        span("section-label") { +label }
        div("fl-chips") {
            projects.forEach { project ->
                a(classes = if (dimmed) "fl-chip fl-chip--dimmed" else "fl-chip") {
                    href = "/worlds/$worldId/projects/${project.id}"
                    span("fl-chip__name") { +project.name }
                    projectStateBadge(project.id, project.state)
                }
            }
        }
    }
}

fun FlowContent.fieldLogDoneShelf(
    worldId: Int,
    done: List<ProjectListItem>,
    cancelled: List<ProjectListItem>,
    archived: List<ProjectListItem>,
) {
    val summaryParts = buildList {
        if (done.isNotEmpty()) add("${done.size} done")
        if (cancelled.isNotEmpty()) add("${cancelled.size} cancelled")
        if (archived.isNotEmpty()) add("${archived.size} archived")
    }

    details("fl-shelf") {
        attributes["id"] = "fl-done-shelf"
        summary("fl-shelf__summary") {
            span("fl-shelf__counts") { +summaryParts.joinToString(" · ") }
            span("fl-shelf__toggle") { +"show" }
        }
        div("fl-shelf-grid") {
            (done + cancelled + archived).forEach { project ->
                a(classes = "fl-shelf-item") {
                    href = "/worlds/$worldId/projects/${project.id}"
                    span("fl-shelf-item__name") { +project.name }
                    projectStateBadge(project.id, project.state)
                }
            }
        }
    }
}
