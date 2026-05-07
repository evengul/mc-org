package app.mcorg.presentation.templated.dsl.components

import app.mcorg.domain.model.invite.Invite
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import java.time.Duration
import java.time.ZonedDateTime

fun FlowContent.pendingInvitationsSection(invitations: List<Invite>) {
    if (invitations.isEmpty()) return
    section {
        id = "pending-invitations-section"
        classes = setOf("pending-invitations")
        div("pending-invitations__header") {
            h2("pending-invitations__heading") { +"Pending invitations" }
            p("pending-invitations__subheading") {
                +"You've been invited to join these worlds."
            }
        }
        div("pending-invitations__list") {
            invitations.forEach { invitationRow(it) }
        }
    }
}

fun FlowContent.invitationRow(invitation: Invite) {
    val role = invitation.role.name.lowercase().replaceFirstChar { it.uppercase() }
    val rowId = "pending-invitation-${invitation.id}"
    div {
        id = rowId
        classes = setOf("invitation-row")
        div("invitation-row__content") {
            p("invitation-row__name") { +invitation.worldName }
            p("invitation-row__meta") {
                +"Invited by ${invitation.fromUsername} • Role: $role • Expires ${formatRelativeExpiry(invitation.expiresAt)}"
            }
        }
        div("invitation-row__actions") {
            button {
                classes = setOf("btn", "btn--ghost")
                type = ButtonType.button
                attributes["hx-patch"] = "/invites/${invitation.id}/decline"
                attributes["hx-target"] = "#$rowId"
                attributes["hx-swap"] = "outerHTML"
                attributes["hx-confirm"] = "Decline invitation to ${invitation.worldName}?"
                +"Decline"
            }
            button {
                classes = setOf("btn", "btn--primary")
                type = ButtonType.button
                attributes["hx-patch"] = "/invites/${invitation.id}/accept"
                attributes["hx-target"] = "#$rowId"
                attributes["hx-swap"] = "outerHTML"
                attributes["hx-confirm"] = "Accept invitation to join ${invitation.worldName}?"
                +"Accept"
            }
        }
    }
}

fun formatRelativeExpiry(expiresAt: ZonedDateTime, now: ZonedDateTime = ZonedDateTime.now(expiresAt.zone)): String {
    val duration = Duration.between(now, expiresAt)
    if (duration.isZero || duration.isNegative) return "now"

    val days = duration.toDays()
    val hours = duration.toHours()
    val minutes = duration.toMinutes()

    return when {
        days >= 2 -> "in $days days"
        days == 1L -> "tomorrow"
        hours >= 2 -> "in $hours hours"
        hours == 1L -> "in 1 hour"
        minutes >= 2 -> "in $minutes minutes"
        minutes == 1L -> "in 1 minute"
        else -> "today"
    }
}
