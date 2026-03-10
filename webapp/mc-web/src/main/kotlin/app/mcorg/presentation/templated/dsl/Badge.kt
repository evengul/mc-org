package app.mcorg.presentation.templated.dsl

import kotlinx.html.FlowContent
import kotlinx.html.span

enum class BadgeStatus(val cssClass: String, val label: String) {
    NOT_STARTED("badge--not-started", "Not Started"),
    IN_PROGRESS("badge--in-progress", "In Progress"),
    DONE("badge--done", "Done"),
    BLOCKED("badge--blocked", "Blocked");
}

fun FlowContent.statusBadge(status: BadgeStatus) {
    span("badge ${status.cssClass}") {
        +status.label
    }
}
