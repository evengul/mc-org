package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.project.ProjectListItem
import app.mcorg.domain.model.project.ProjectResourceEdge
import app.mcorg.domain.model.resources.ResourceGatheringItem
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.summary

/**
 * Field Log — state-sorted sections of the world's project list (execute view).
 * Active projects get full rows with dependency captions; pending/paused
 * collapse to chips; terminal states (done/cancelled/archived) share a single
 * collapsed shelf. Blocked projects sink to the bottom of Active.
 */

fun FlowContent.fieldLogSections(
    worldId: Int,
    projects: List<ProjectListItem>,
    edges: List<ProjectResourceEdge> = emptyList(),
    resume: ResumeHeroData? = null,
    resumeSort: ResumeSort = ResumeSort.NEEDED,
) {
    val model = FieldLogModel.of(projects, edges)

    if (resume != null) {
        resumeHero(worldId, resume, feeds = model.feeds(resume.project.id), sort = resumeSort)
    }

    val activeRows = model.active.filter { it.id != resume?.project?.id }
    if (activeRows.isNotEmpty()) {
        div("fl-section") {
            attributes["id"] = "fl-active-section"
            div("fl-section__head") {
                span("section-label") { +"Active · ${model.active.size}" }
                if (activeRows.any { model.isBlocked(it.id) }) {
                    span("fl-section__hint") { +"sorted: unblocked first · blocked sinks to the bottom" }
                }
            }
            div("fl-row-list") {
                attributes["id"] = "fl-active-list"
                activeRows.forEach { fieldLogRow(worldId, it, model) }
            }
        }
    }

    if (model.pending.isNotEmpty()) {
        fieldLogChipSection("fl-pending-section", "Pending · ${model.pending.size}", worldId, model.pending, dimmed = false)
    }

    if (model.paused.isNotEmpty()) {
        fieldLogChipSection("fl-paused-section", "Paused · ${model.paused.size}", worldId, model.paused, dimmed = true)
    }

    if (model.done.isNotEmpty() || model.cancelled.isNotEmpty() || model.archived.isNotEmpty()) {
        fieldLogDoneShelf(worldId, model.done, model.cancelled, model.archived)
    }
}

fun FlowContent.fieldLogRow(worldId: Int, project: ProjectListItem, model: FieldLogModel) {
    fieldLogRow(worldId, project, model.feeds(project.id), model.blockedBy(project.id))
}

fun FlowContent.fieldLogRow(
    worldId: Int,
    project: ProjectListItem,
    feeds: List<ProjectResourceEdge>,
    blockedBy: List<ProjectResourceEdge>,
    expanded: Boolean = false,
    sliceItems: List<ResourceGatheringItem> = emptyList(),
) {
    val blocked = blockedBy.isNotEmpty()

    div(if (blocked) "fl-row fl-row--blocked" else "fl-row") {
        attributes["id"] = "fl-row-${project.id}"
        div("fl-row__head fl-row__head--clickable") {
            attributes["hx-get"] =
                "/worlds/$worldId/projects/${project.id}/field-log-row?expanded=${!expanded}"
            attributes["hx-target"] = "#fl-row-${project.id}"
            attributes["hx-swap"] = "outerHTML"
            div("fl-row__main") {
                div("fl-row__title") {
                    a(classes = "fl-row__name") {
                        href = "/worlds/$worldId/projects/${project.id}"
                        +project.name
                    }
                    if (blocked) {
                        span("badge badge--blocked") { +"Blocked" }
                    } else {
                        projectStateBadge(project.id, project.state)
                    }
                    if (project.itemCount > 0) {
                        span("fl-row__sub") { +"${project.itemCount} item${if (project.itemCount == 1) "" else "s"}" }
                    }
                }
                if (blocked) {
                    span("fl-row__caption fl-row__caption--blocked") {
                        +"Blocked by ← ${blockedBy.toEdgeCaption { it.producerName }}"
                    }
                } else if (feeds.isNotEmpty()) {
                    span("fl-row__caption") {
                        +"Feeds → ${feeds.toEdgeCaption { it.consumerName }}"
                    }
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
                span("fl-row__chevron") { +if (expanded) "collapse ▲" else "expand ▾" }
            }
        }
        if (expanded) {
            fieldLogSlice(worldId, project, sliceItems, blockedBy)
        }
    }
}

fun fieldLogRowFragment(
    worldId: Int,
    project: ProjectListItem,
    feeds: List<ProjectResourceEdge>,
    blockedBy: List<ProjectResourceEdge>,
    expanded: Boolean,
    sliceItems: List<ResourceGatheringItem> = emptyList(),
): String = kotlinx.html.stream.createHTML().div {
    fieldLogRow(worldId, project, feeds, blockedBy, expanded, sliceItems)
}.removePrefix("<div>").removeSuffix("</div>")

/** "Slime Farm · sticky piston  ·  Iron Farm · hopper" — one entry per counterpart project. */
private fun List<ProjectResourceEdge>.toEdgeCaption(counterpart: (ProjectResourceEdge) -> String): String =
    groupBy(counterpart)
        .entries
        .joinToString("  ·  ") { (name, projectEdges) ->
            val items = projectEdges.mapNotNull { it.itemName }.distinct()
            if (items.isEmpty()) name else "$name · ${items.joinToString(", ")}"
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
