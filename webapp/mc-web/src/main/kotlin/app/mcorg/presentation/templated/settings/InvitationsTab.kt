package app.mcorg.presentation.templated.settings

import app.mcorg.domain.model.invite.Invite
import app.mcorg.domain.model.invite.InviteStatus
import app.mcorg.domain.model.user.Role
import app.mcorg.pipeline.world.invitations.CountWorldInvitationsResult
import app.mcorg.pipeline.world.invitations.InvitationStatusFilter
import app.mcorg.presentation.*
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.dsl.BadgeVariant
import app.mcorg.presentation.templated.dsl.TabItem
import app.mcorg.presentation.templated.dsl.TabVariant
import app.mcorg.presentation.templated.dsl.avatar
import app.mcorg.presentation.templated.dsl.badge
import app.mcorg.presentation.templated.dsl.personRow
import app.mcorg.presentation.templated.dsl.section
import app.mcorg.presentation.templated.dsl.tabStrip
import app.mcorg.presentation.templated.utils.formatAsDate
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*

val validInitialRoles = listOf(
    Role.MEMBER,
    Role.ADMIN
)

fun DIV.invitationsSection(data: SettingsPageData) {
    section(
        title = "Invitations",
        subtitle = "Invite players to this world and review their status.",
    ) {
        div("section__card") {
            sendInvitationForm(data.world.id)
        }
        invitationsListWithFilter(data.world.id, data.invitations, data.invitationCounts, data.statusFilter)
    }
}

fun DIV.sendInvitationForm(worldId: Int) {
    div("send-invitation") {
        h3 { +"Send invitation" }
        form("send-invitation__form") {
            encType = FormEncType.applicationXWwwFormUrlEncoded
            hxTarget("#invitation-list")
            attributes["hx-target-error"] = ".validation-error-message"
            hxSwap("afterbegin")
            hxPost("${Link.Worlds.world(worldId).to}/settings/members/invitations")
            attributes["hx-on::after-request"] = """
                if (event.detail.xhr.status >= 200 && event.detail.xhr.status < 300) {
                        this.reset(); document.querySelectorAll('.validation-error-message').forEach(el => el.innerHTML = '');
                }
            """.trimIndent()
            div("send-invitation__inputs") {
                div("input-group") {
                    label {
                        htmlFor = "invite-username-input"
                        +"Minecraft Username"
                        span("required-indicator") { +"*" }
                    }
                    input(type = InputType.text, name = "toUsername", classes = "form-control") {
                        id = "invite-username-input"
                        placeholder = "Alex"
                        required = true
                    }
                }
                div("input-group") {
                    label {
                        htmlFor = "invite-role-input"
                        +"Role"
                        span("required-indicator") { +"*" }
                    }
                    select(classes = "form-control") {
                        id = "invite-role-input"
                        name = "role"
                        validInitialRoles.forEach {
                            option {
                                value = it.name
                                selected = it == Role.MEMBER
                                +it.toPrettyEnumName()
                            }
                        }
                    }
                }
            }
            p("validation-error-message") { id = "validation-error-toUsername" }
            p("validation-error-message") { id = "validation-error-role" }
            div("send-invitation__actions") {
                button {
                    classes = setOf("btn", "btn--primary")
                    type = ButtonType.submit
                    +"Send Invitation"
                }
            }
        }
    }
}

fun DIV.invitationsListWithFilter(
    worldId: Int,
    invitations: List<Invite>,
    counts: CountWorldInvitationsResult,
    selectedFilter: InvitationStatusFilter = InvitationStatusFilter.PENDING
) {
    div("invitations-list-section") {
        h3 { +"Invitations" }
        worldInvitationTabs(worldId, counts, selectedFilter)
        ul { worldInvitations(invitations) }
    }
}

/**
 * Emits the `<div id="invitation-status-filter">` container with the pill tabs inside.
 *
 * Pass `oob = true` when this container is part of an HTMX response that should swap
 * the existing `#invitation-status-filter` element out-of-band. `hx-swap-oob` must live
 * on the same element as the matched id, so this function attaches both itself.
 */
fun FlowContent.worldInvitationTabs(
    worldId: Int,
    counts: CountWorldInvitationsResult,
    selectedFilter: InvitationStatusFilter = InvitationStatusFilter.PENDING,
    oob: Boolean = false,
) {
    val historyCount = counts.all - counts.pending
    val basePath = "/worlds/$worldId/settings"
    val filterTabs = listOf(
        TabItem("pending", "Pending (${counts.pending})", "$basePath?status=pending"),
        TabItem("history", "History ($historyCount)", "$basePath?status=history"),
    )
    div("invitation-status-filter") {
        id = "invitation-status-filter"
        if (oob) hxOutOfBands("true")
        tabStrip(
            tabs = filterTabs,
            activeValue = when (selectedFilter) {
                InvitationStatusFilter.PENDING -> "pending"
                else -> "history"
            },
            hxTarget = "#invitation-list",
            variant = TabVariant.PILLS,
            queryName = "status",
        )
    }
}

/** Top-level fragment renderer so the OOB div lives at the response root, not nested. */
fun renderInvitationStatusFilterOob(
    worldId: Int,
    counts: CountWorldInvitationsResult,
    selectedFilter: InvitationStatusFilter,
): String = kotlinx.html.stream.createHTML().div("invitation-status-filter") {
    id = "invitation-status-filter"
    hxOutOfBands("true")
    val historyCount = counts.all - counts.pending
    val basePath = "/worlds/$worldId/settings"
    val filterTabs = listOf(
        TabItem("pending", "Pending (${counts.pending})", "$basePath?status=pending"),
        TabItem("history", "History ($historyCount)", "$basePath?status=history"),
    )
    tabStrip(
        tabs = filterTabs,
        activeValue = when (selectedFilter) {
            InvitationStatusFilter.PENDING -> "pending"
            else -> "history"
        },
        hxTarget = "#invitation-list",
        variant = TabVariant.PILLS,
        queryName = "status",
    )
}

fun UL.worldInvitations(invitations: List<Invite>) {
    id = "invitation-list"
    classes = classes + "person-row-list"
    if (invitations.isEmpty()) {
        personRow(rowId = "empty-invitations-list", empty = true) {
            start { p("subtle") { +"No invitations found with this status." } }
        }
        return
    }
    invitations.sortedByDescending { it.createdAt }.forEach { invite ->
        personRow(rowId = "invite-${invite.id}") {
            start { inviteRowStart(invite) }
            if (invite.status is InviteStatus.Pending) {
                end { inviteRowEnd(invite) }
            }
        }
    }
}

fun FlowContent.inviteRowStart(invite: Invite) {
    avatar(invite.toUsername)
    div("person-row__info") {
        p("person-row__name") { +invite.toUsername }
        div("row") {
            badge(invite.role.toPrettyEnumName(), BadgeVariant.NEUTRAL)
            badge(invite.status::class.simpleName!!, BadgeVariant.ACCENT)
        }
        div("row") {
            p("subtle") { +"Sent: ${invite.createdAt.formatAsDate()}" }
            p("subtle") { +"•" }
            p("subtle") { +"Expires: ${invite.expiresAt.formatAsDate()}" }
        }
    }
}

fun FlowContent.inviteRowEnd(invite: Invite) {
    button {
        classes = setOf("btn", "btn--ghost", "btn--sm")
        type = ButtonType.button
        hxDeleteWithConfirm(
            url = Link.Worlds.world(invite.worldId).settings().to + "/members/invitations/${invite.id}",
            title = "Delete Invite",
            description = "Are you sure you want to cancel this invitation? You may resend it later."
        )
        hxTarget("#invite-${invite.id}")
        hxSwap("delete")
        +"Cancel"
    }
}
