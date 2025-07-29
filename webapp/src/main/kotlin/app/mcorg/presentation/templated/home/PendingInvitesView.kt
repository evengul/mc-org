package app.mcorg.presentation.templated.home

import app.mcorg.domain.model.invite.Invite
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.chip.ChipVariant
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.icon.IconColor
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.icon.iconComponent
import kotlinx.html.TagConsumer
import kotlinx.html.UL
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul
import java.time.format.DateTimeFormatter

fun UL.pendingInvitesListView(invites: List<Invite>) {
    id = "home-pending-invites-list"
    invites.forEach { invite ->
        li {
            div("home-pending-invite-details") {
                p {
                    classes += "home-pending-invite-title"
                    + invite.worldName
                }
                div("home-pending-invite-details-info") {
                    chipComponent {
                        classes += "subtle"
                        variant = ChipVariant.INFO
                        text = "Role: ${invite.role.name.lowercase().replaceFirstChar { it.uppercase() }}"
                    }
                    div("home-pending-invite-details-info-expires") {
                        // TODO: Clock icon
                        iconComponent(Icons.ADD_WORLD, color = IconColor.ON_BACKGROUND, size = IconSize.SMALL)
                        p("subtle") {
                            + "Expires ${invite.expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
                        }
                    }
                    p("home-pending-invite-details-info-from subtle") {
                        + "Invited by: ${invite.fromUsername}"
                    }
                }
            }
            div("home-pending-invite-actions") {
                neutralButton("Decline") {
                    iconLeft = Icons.CLOSE
                    iconSize = IconSize.SMALL
                }
                actionButton("Accept") {
                    // TODO: Icon for accept
                    iconLeft = Icons.ADD_WORLD
                    iconSize = IconSize.SMALL
                }
            }
        }
    }
}

data class PendingInvitesView(private val invites: List<Invite>) : LeafComponent() {
    override fun render(container: TagConsumer<*>) {
        container.div {
            id = "home-pending-invites"
            div {
                id = "home-pending-invites-header"
                h2 {
                    // TODO: Bell icon
                    iconComponent(Icons.Notification.INFO, color = IconColor.ACTION)
                    + "Pending Invitations"
                }
                p("subtle") {
                    + "You have been invited to join these worlds."
                }
            }
            ul {
                pendingInvitesListView(invites)
            }
        }
    }
}