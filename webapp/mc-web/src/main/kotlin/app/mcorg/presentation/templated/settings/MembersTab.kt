package app.mcorg.presentation.templated.settings

import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.user.WorldMember
import app.mcorg.presentation.*
import app.mcorg.presentation.templated.dsl.Link
import app.mcorg.presentation.templated.dsl.avatar
import app.mcorg.presentation.templated.dsl.personRow
import app.mcorg.presentation.templated.dsl.section
import app.mcorg.presentation.templated.utils.formatAsRelativeOrDate
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*

fun DIV.membersSection(currentUser: TokenProfile, members: List<WorldMember>) {
    section(
        title = "World Members",
        subtitle = "Only the owner can delete a world. Admins can see and use these settings. Members can create and work with projects in a world.",
    ) {
        membersList(currentUser, members)
    }
}

private fun DIV.membersList(currentUser: TokenProfile, members: List<WorldMember>) {
    val currentMember = members.find { it.id == currentUser.id }
    ul("person-row-list") {
        members.forEach { member ->
            personRow(rowId = "member-${member.id}") {
                start {
                    avatar(member.displayName)
                    div("person-row__info") {
                        p("person-row__name") { +member.displayName }
                        p("person-row__meta subtle") {
                            span {
                                id = "member-${member.id}-role-display"
                                +member.worldRole.toPrettyEnumName()
                            }
                            +" • "
                            span { +"Joined: ${member.createdAt.formatAsRelativeOrDate()}" }
                        }
                    }
                }
                if (member.worldRole != Role.OWNER && currentMember != null && currentMember.worldRole.isHigherThan(member.worldRole)) {
                    end {
                        select(classes = "form-control form-control--sm") {
                            name = "role"
                            hxPatch("${Link.Worlds.world(member.worldId).to}/settings/members/${member.id}/role")
                            hxTarget("#member-${member.id}-role-display")
                            hxSwap("innerHTML")
                            hxTrigger("change")
                            Role.entries.filter { it != Role.BANNED && it != Role.OWNER }.forEach { role ->
                                option {
                                    value = role.name
                                    selected = role == member.worldRole
                                    +role.toPrettyEnumName()
                                }
                            }
                        }
                        button {
                            classes = setOf("btn", "btn--danger", "btn--sm")
                            type = ButtonType.button
                            hxDeleteWithConfirm(
                                url = "${Link.Worlds.world(member.worldId).to}/settings/members/${member.id}",
                                title = "Remove Member",
                                description = "Are you sure you want to remove ${member.displayName} from this world?"
                            )
                            hxTarget("#member-${member.id}")
                            hxSwap("delete")
                            +"Remove"
                        }
                    }
                }
            }
        }
    }
}
