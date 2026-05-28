package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.project.ProjectListItem
import app.mcorg.domain.model.project.ProjectStage
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.span

fun ProjectStage.toDisplayStatus(): String = when (this) {
    ProjectStage.IDEA, ProjectStage.DESIGN, ProjectStage.PLANNING -> "not-started"
    ProjectStage.RESOURCE_GATHERING, ProjectStage.BUILDING, ProjectStage.TESTING -> "in-progress"
    ProjectStage.COMPLETED -> "done"
}

fun ProjectStage.toBadgeStatus(): BadgeStatus = when (this) {
    ProjectStage.IDEA, ProjectStage.DESIGN, ProjectStage.PLANNING -> BadgeStatus.NOT_STARTED
    ProjectStage.RESOURCE_GATHERING, ProjectStage.BUILDING, ProjectStage.TESTING -> BadgeStatus.IN_PROGRESS
    ProjectStage.COMPLETED -> BadgeStatus.DONE
}

fun FlowContent.projectCard(worldId: Int, project: ProjectListItem) {
    val isDone = project.stage == ProjectStage.COMPLETED
    val cardClasses = buildSet {
        add("project-card")
        if (isDone) add("project-card--done")
    }

    div(cardClasses.joinToString(" ")) {
        attributes["id"] = "project-card-${project.id}"

        div("project-card__header") {
            a(classes = "project-card__name") {
                href = "/worlds/$worldId/projects/${project.id}"
                +project.name
            }
            statusBadge(project.stage.toBadgeStatus())
        }

        div("project-card__meta") {
            if (project.tasksTotal > 0) {
                span("project-card__tasks") {
                    +"${project.tasksDone} of ${project.tasksTotal} task${if (project.tasksTotal == 1) "" else "s"} done"
                }
            }
        }

        if (project.resourcesRequired > 0) {
            div("project-card__resource-header") {
                span("project-card__next-task-label") { +"Resources" }
                span("project-card__next-task-label") { +"${formatItemCount(project.resourcesGathered)} / ${formatItemCount(project.resourcesRequired)}" }
            }
            progressBar(project.resourcesGathered, project.resourcesRequired, large = true)
        }

        val nextTask = project.nextTaskName
        if (nextTask != null) {
            p("project-card__next-task") {
                span("project-card__next-task-label") { +"Next: " }
                +nextTask
            }
        }
    }
}

fun FlowContent.projectCardList(worldId: Int, projects: List<ProjectListItem>) {
    div("project-card-list") {
        attributes["id"] = "project-card-list"
        projects.forEach { projectCard(worldId, it) }
    }
}
