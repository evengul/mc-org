package app.mcorg.presentation.templated.dsl

import kotlinx.html.FlowContent
import kotlinx.html.span

enum class BadgeStatus(val cssClass: String, val label: String) {
    NOT_STARTED("badge--not-started", "Not Started"),
    IN_PROGRESS("badge--in-progress", "In Progress"),
    DONE("badge--done", "Done"),
    BLOCKED("badge--blocked", "Blocked");
}

enum class BadgeVariant(val cssClass: String) {
    NEUTRAL("badge--neutral"),
    ACCENT("badge--status"),
    DANGER("badge--blocked"),
}

fun FlowContent.statusBadge(status: BadgeStatus) {
    span("badge ${status.cssClass}") {
        +status.label
    }
}

fun FlowContent.badge(label: String, variant: BadgeVariant = BadgeVariant.NEUTRAL) {
    span("badge ${variant.cssClass}") {
        +label
    }
}
