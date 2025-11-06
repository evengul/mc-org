package app.mcorg.presentation.templated.settings

import app.mcorg.domain.model.invite.Invite
import app.mcorg.domain.model.invite.InviteStatus
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.user.WorldMember
import app.mcorg.pipeline.world.invitations.CountWorldInvitationsResult
import app.mcorg.pipeline.world.invitations.InvitationStatusFilter
import app.mcorg.presentation.*
import app.mcorg.presentation.templated.common.avatar.avatar
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.dangerButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.icon.IconColor
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.tabs.TabData
import app.mcorg.presentation.templated.common.tabs.TabsVariant
import app.mcorg.presentation.templated.common.tabs.tabsComponent
import app.mcorg.presentation.templated.utils.formatAsDate
import app.mcorg.presentation.templated.utils.formatAsRelativeOrDate
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*

val validInitialRoles = listOf(
    Role.MEMBER,
    Role.ADMIN
)

fun DIV.sendInvitationForm(worldId: Int) {
    section(classes = "send-invitation") {
        h2 {
            +"Send invitation"
        }
        p("subtle") {
            +"Invite new members to collaborate on this world"
        }
        form {
            encType = FormEncType.applicationXWwwFormUrlEncoded
            hxTarget(".invitation-list")
            hxSwap("afterbegin")
            hxPost("${Link.Worlds.world(worldId).to}/settings/members/invitations")
            attributes["hx-on::after-request"] = "this.reset();"
            div("inputs") {
                div("input-group") {
                    label {
                        + "Minecraft Username"
                        span("required-indicator") { +"*" }
                    }
                    input(type = InputType.text, name = "toUsername") {
                        placeholder = "Alex"
                        required = true
                    }
                }
                div("input-group") {
                    label {
                        + "Role"
                        span("required-indicator") { +"*" }
                    }
                    select {
                        name = "role"
                        validInitialRoles.forEach {
                            option {
                                value = it.name
                                selected = it == Role.MEMBER
                                + it.toPrettyEnumName()
                            }
                        }
                    }
                }
            }
            actionButton("Send Invitation")
        }
    }
}

fun DIV.invitationsListWithFilter(invitations: List<Invite>, counts: CountWorldInvitationsResult) {
    section("member-invitations") {
        h2 {
            +"World invitations"
        }
        div {
           worldInvitationTabs(counts)
        }
        ul {
            worldInvitations(invitations)
        }
    }
}

fun DIV.worldInvitationTabs(counts: CountWorldInvitationsResult, selectedFilter: InvitationStatusFilter = InvitationStatusFilter.PENDING) {
    classes += "invitation-status-filter"
    id = "invitation-status-filter"
    tabsComponent(
        TabData.create("pending", "Pending (${counts.pending})"),
        TabData.create("accepted", "Accepted (${counts.accepted})"),
        TabData.create("declined", "Declined (${counts.declined})"),
        TabData.create("expired", "Expired (${counts.expired})"),
        TabData.create("cancelled", "Cancelled (${counts.cancelled})"),
        TabData.create("all", "All (${counts.all})", )
    ) {
        activeTab = selectedFilter.name.lowercase()
        queryName = "status"
        hxTarget = ".invitation-list"
        variant = TabsVariant.PILLS
    }
}

fun UL.worldInvitations(invitations: List<Invite>) {
    classes += "invitation-list"
    if (invitations.isEmpty()) {
        li {
            id = "empty-invitations-list"
            p("subtle") {
                +"No invitations found with this status."
            }
        }
        return
    }
    invitations.sortedByDescending { it.createdAt }.forEach { invite ->
        li {
            worldInvite(invite)
        }
    }
}

fun LI.worldInvite(invite: Invite) {
    id = "invite-${invite.id}"
    div("invitation-item-start") {
        div {
            avatar(color = IconColor.ON_BACKGROUND, size = IconSize.MEDIUM)
        }
        div("invitation-item-details") {
            p {
                +invite.toUsername
            }
            div("row") {
                chipComponent {
                    +invite.role.toPrettyEnumName()
                }
                chipComponent {
                    +invite.status::class.simpleName!!
                }
            }
            div("row") {
                p("subtle") {
                    +"Sent: ${invite.createdAt.formatAsDate()}"
                }
                p("subtle") {
                    + "•"
                }
                p("subtle") {
                    +"Expires: ${invite.expiresAt.formatAsDate()}"
                }
            }
        }
    }
    div("invitation-item-end") {
        if (invite.status is InviteStatus.Pending) {
            neutralButton("Cancel") {
                buttonBlock = {
                    hxDeleteWithConfirm(
                        url = Link.Worlds.world(invite.worldId).settings().to + "/members/invitations/${invite.id}",
                        title = "Delete Invite",
                        description = "Are you sure you want to cancel this invitation? You may resend it later."
                    )
                    hxTarget("#invite-${invite.id}")
                    hxSwap("delete")
                }
            }
        }
    }
}

fun DIV.membersListSection(currentUser: TokenProfile, members: List<WorldMember>) {
    val currentMember = members.find { it.id == currentUser.id }
    section("member-list") {
        h2 {
            +"World Members"
        }
        p("subtle") {
            + "Manage who has access to this world. There is only one owner, and only they can delete a world. Admins can see and use these settings. Members can create and work with projects in a world."
        }
        ul {
            members.forEach { member ->
                li {
                    id = "member-${member.id}"
                    div("member-item-start") {
                        avatar(color = IconColor.ON_BACKGROUND, size = IconSize.MEDIUM)
                        div {
                            p {
                                + member.displayName
                            }
                            p("subtle") {
                                span {
                                    id = "member-${member.id}-role-display"
                                    + member.worldRole.toPrettyEnumName()
                                }
                                + " • "
                                span {
                                    + "Joined: ${member.createdAt.formatAsRelativeOrDate()}"
                                }
                            }
                        }
                    }
                    if (member.worldRole != Role.OWNER && currentMember != null && currentMember.worldRole.isHigherThan(member.worldRole)) {
                        div("member-item-action") {
                            select {
                                name = "role"
                                hxPatch("${Link.Worlds.world(member.worldId).to}/settings/members/${member.id}/role")
                                hxTarget("#member-${member.id}-role-display")
                                hxSwap("innerHTML")
                                hxTrigger("change")
                                Role.entries.filter { it != Role.BANNED && it != Role.OWNER }.map { role ->
                                    option {
                                        value = role.name
                                        selected = role == member.worldRole
                                        + role.toPrettyEnumName()
                                    }
                                }
                            }
                            dangerButton("Remove member") {
                                buttonBlock = {
                                    hxDeleteWithConfirm(
                                        url = "${Link.Worlds.world(member.worldId).to}/settings/members/${member.id}",
                                        title = "Remove Member",
                                        description = "Are you sure you want to remove ${member.displayName} from this world?"
                                    )
                                    hxTarget("#member-${member.id}")
                                    hxSwap("delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun DIV.membersTab(tabData: SettingsTab.Members) {
    val (world, currentUser, invitations, invitationCounts, members) = tabData
    classes += "settings-members-tab world-settings-content"

    sendInvitationForm(world.id)
    invitationsListWithFilter(invitations, invitationCounts)
    membersListSection(currentUser, members)
}
