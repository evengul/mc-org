package app.mcorg.presentation.templated.settings

import app.mcorg.domain.model.invite.Invite
import app.mcorg.domain.model.invite.InviteStatus
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.WorldMember
import app.mcorg.pipeline.world.GetWorldInvitationsInput
import app.mcorg.presentation.hxConfirm
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
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
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.DIV
import kotlinx.html.FormEncType
import kotlinx.html.InputType
import kotlinx.html.LI
import kotlinx.html.UL
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.select
import kotlinx.html.ul
import java.time.format.DateTimeFormatter

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
                    }
                    input(type = InputType.text, name = "toUsername") {
                        placeholder = "Alex"
                        required = true
                    }
                }
                div("input-group") {
                    label {
                        + "Role"
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

fun DIV.invitationsListWithFilter(invitations: List<Invite>, counts: Map<GetWorldInvitationsInput.StatusFilter, Int>) {
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

fun DIV.worldInvitationTabs(counts: Map<GetWorldInvitationsInput.StatusFilter, Int>, selectedFilter: GetWorldInvitationsInput.StatusFilter = GetWorldInvitationsInput.StatusFilter.PENDING) {
    classes += "invitation-status-filter"
    id = "invitation-status-filter"
    tabsComponent(
        TabData.create("pending", "Pending (${counts[GetWorldInvitationsInput.StatusFilter.PENDING] ?: 0})"),
        TabData.create("accepted", "Accepted (${counts[GetWorldInvitationsInput.StatusFilter.ACCEPTED] ?: 0})"),
        TabData.create("declined", "Declined (${counts[GetWorldInvitationsInput.StatusFilter.DECLINED] ?: 0})"),
        TabData.create("expired", "Expired (${counts[GetWorldInvitationsInput.StatusFilter.EXPIRED] ?: 0})"),
        TabData.create("cancelled", "Cancelled (${counts[GetWorldInvitationsInput.StatusFilter.CANCELLED] ?: 0})"),
        TabData.create("all", "All (${counts[GetWorldInvitationsInput.StatusFilter.ALL] ?: 0})", )
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
                    +"Sent on: ${invite.createdAt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}"
                }
                p("subtle") {
                    + "•"
                }
                p("subtle") {
                    +"Expires on: ${invite.expiresAt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}"
                }
            }
        }
    }
    div("invitation-item-end") {
        if (invite.status is InviteStatus.Pending) {
            neutralButton("Cancel") {
                buttonBlock = {
                    hxDelete(Link.Worlds.world(invite.worldId).settings().to + "/members/invitations/${invite.id}")
                    hxTarget("#invite-${invite.id}")
                    hxSwap("delete")
                    hxConfirm("Are you sure you want to cancel this invitation? You may resend it later.")
                }
            }
        }
    }
}

fun DIV.membersListSection(members: List<WorldMember>) {
    section("member-list") {
        h2 {
            +"World Members"
        }
        p("subtle") {
            + "Manage who has access to this world"
        }
        ul {
            members.forEach { member ->
                li {
                    div("member-item-start") {
                        avatar(color = IconColor.ON_BACKGROUND, size = IconSize.MEDIUM)
                        div {
                            p {
                                + member.displayName
                            }
                            p("subtle") {
                                +listOf(
                                    member.worldRole.toPrettyEnumName(),
                                    "Joined on: ${member.createdAt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}"
                                ).joinToString(" • ")
                            }
                        }
                    }
                    div("member-item-action") {
                        neutralButton("Change role...")
                        dangerButton("Remove member")
                        dangerButton("Ban member")
                    }
                }
            }
        }
    }
}

fun DIV.membersTab(tabData: SettingsTab.Members) {
    val (world, invitations, invitationCounts, members) = tabData
    classes += "settings-members-tab world-settings-content"

    sendInvitationForm(world.id)
    invitationsListWithFilter(invitations, invitationCounts)
    membersListSection(members)
}
