package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.project.ProjectState
import kotlinx.html.FlowContent
import kotlinx.html.span
import kotlinx.html.stream.createHTML

val ProjectState.label: String
    get() = when (this) {
        ProjectState.PENDING -> "Pending"
        ProjectState.ACTIVE -> "Active"
        ProjectState.PAUSED -> "Paused"
        ProjectState.DONE -> "Done"
        ProjectState.CANCELLED -> "Cancelled"
        ProjectState.ARCHIVED -> "Archived"
    }

val ProjectState.badgeModifier: String
    get() = when (this) {
        ProjectState.PENDING -> "badge--not-started"
        ProjectState.ACTIVE -> "badge--in-progress"
        ProjectState.PAUSED -> "badge--neutral"
        ProjectState.DONE -> "badge--done"
        ProjectState.CANCELLED -> "badge--neutral"
        ProjectState.ARCHIVED -> "badge--neutral"
    }

fun FlowContent.projectStateBadge(projectId: Int, state: ProjectState) {
    span("badge ${state.badgeModifier}") {
        attributes["id"] = "project-state-badge-$projectId"
        +state.label
    }
}

fun projectStateBadgeFragment(projectId: Int, state: ProjectState): String =
    createHTML().span("badge ${state.badgeModifier}") {
        attributes["id"] = "project-state-badge-$projectId"
        +state.label
    }
