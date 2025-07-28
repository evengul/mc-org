package app.mcorg.presentation.templated.settings

import app.mcorg.domain.model.invite.InviteStatus
import app.mcorg.domain.model.user.Role
import app.mcorg.presentation.templated.common.avatar.avatar
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.dangerButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.icon.IconColor
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.DIV
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.onClick
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.select
import kotlinx.html.ul
import java.time.format.DateTimeFormatter

fun DIV.membersTab(tabData: SettingsTab.Members) {
    val (_, invitations, members) = tabData
    classes += "settings-members-tab"
    section(classes = "send-invitation") {
        h2 {
            +"Send invitation"
        }
        p("subtle") {
            +"Invite new members to collaborate on this world"
        }
        form {
            div("inputs") {
                div("input-group") {
                    label {
                        +"Minecraft Username"
                    }
                    input(type = InputType.text, name = "username") {
                        placeholder = "Alex"
                        required = true
                    }
                }
                div("input-group") {
                    label {
                        +"Role"
                    }
                    select {
                        name = "role"
                        Role.entries.filter { it != Role.BANNED }.forEach {
                            option {
                                value = it.name
                                selected = it == Role.MEMBER
                                +it.toPrettyEnumName()
                            }
                        }
                    }
                }
            }
            actionButton("Send Invitation")
        }
    }
    section("member-invitations") {
        h2 {
            +"World invitations"
        }
        div("invitation-status-filter") {
            val onClickHandler = "this.classList.toggle('selected'); document.querySelectorAll('.invitation-status-filter button').forEach(btn => { if (btn !== this) btn.classList.remove('selected'); });"
            button {
                classes += "selected"
                onClick = onClickHandler
                +"Pending (${invitations.count { it.status is InviteStatus.Pending }})"
            }
            button {
                onClick = onClickHandler
                +"Accepted (${invitations.count { it.status is InviteStatus.Accepted }})"
            }
            button {
                onClick = onClickHandler
                +"Declined (${invitations.count { it.status is InviteStatus.Declined }})"
            }
            button {
                onClick = onClickHandler
                +"Expired (${invitations.count { it.status is InviteStatus.Expired }})"
            }
            button {
                onClick = onClickHandler
                +"Cancelled (${invitations.count { it.status is InviteStatus.Cancelled }})"
            }
            button {
                onClick = onClickHandler
                +"All (${invitations.size})"
            }
        }
        ul("invitation-list") {
            invitations.sortedByDescending { it.createdAt }.forEach { invite ->
                li {
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
                                    +invite.status.javaClass.simpleName
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
                       when (invite.status) {
                           is InviteStatus.Pending -> neutralButton("Resend")
                           is InviteStatus.Accepted -> chipComponent {
                               +"Accepted"
                           }

                           is InviteStatus.Declined,
                           is InviteStatus.Expired,
                           is InviteStatus.Cancelled -> neutralButton("Resend")
                       }
                   }
                }
            }
        }
    }
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