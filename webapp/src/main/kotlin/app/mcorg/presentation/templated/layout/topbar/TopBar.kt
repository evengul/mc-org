package app.mcorg.presentation.templated.layout.topbar

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.templated.common.button.IconButtonColor
import app.mcorg.presentation.templated.common.button.iconButton
import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.link.linkComponent
import kotlinx.html.BODY
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.header
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.span
import kotlinx.html.ul

fun BODY.topBar(user: TokenProfile? = null, unreadNotificationCount: Int = 0) =
    addComponent(TopBar(user, unreadNotificationCount))

enum class ActiveLinks {
    HOME,
    IDEAS,
    SERVERS,
    ADMIN_DASHBOARD,
    THEME_TOGGLE,
    NOTIFICATIONS,
    PROFILE
}

data class TopBar(
    val user: TokenProfile? = null,
    val unreadNotificationCount: Int = 0,
    val activeLinks: Set<ActiveLinks> = setOf(
        ActiveLinks.HOME,
        ActiveLinks.IDEAS,
        ActiveLinks.NOTIFICATIONS,
        ActiveLinks.ADMIN_DASHBOARD,
        ActiveLinks.THEME_TOGGLE,
        ActiveLinks.PROFILE
    ),
) : LeafComponent() {
    override fun render(container: TagConsumer<*>) {
        container.header {
            classes += "top-bar"
            h1 { + "MC-ORG${if (user?.isDemoUserInProduction == true) " (DEMO)" else ""}" }
            nav {
                classes += "top-bar-nav"
                ul {
                    classes += "top-bar-links"
                    if (activeLinks.contains(ActiveLinks.HOME)) {
                        li {
                            classes += "top-bar-link"
                            linkComponent(Link.Home) {
                                +"Home"
                            }
                        }
                    }
                    if (activeLinks.contains(ActiveLinks.IDEAS) && user != null) {
                        li {
                            classes += "top-bar-link"
                            linkComponent(Link.Ideas) {
                                + "Idea Bank"
                            }
                        }
                    }
                    if (activeLinks.contains(ActiveLinks.SERVERS)) {
                        li {
                            classes += "top-bar-link"
                            linkComponent(Link.Servers) {
                                + "Servers"
                            }
                        }
                    }
                    if (activeLinks.contains(ActiveLinks.ADMIN_DASHBOARD) && user?.isSuperAdmin == true && !user.isDemoUserInProduction) {
                        li {
                            classes += "top-bar-link"
                            linkComponent(Link.AdminDashboard) {
                                + "Admin"
                            }
                        }
                    }
                }
                div {
                    classes += "top-bar-right"
                    if (activeLinks.contains(ActiveLinks.THEME_TOGGLE)) {
                        iconButton(Icons.Dimensions.OVERWORLD, "Cycle visual themes", iconSize = IconSize.SMALL) {
                            buttonBlock = {
                                attributes["onclick"] = "window.mcorgTheme.cycle()"
                            }
                        }
                    }
                    if (user != null) {
                        if (activeLinks.contains(ActiveLinks.NOTIFICATIONS)) {
                            a(href = Link.Notifications.to, classes = "top-bar-notification") {
                                iconButton(Icons.Notification.INFO, "Notifications ($unreadNotificationCount)", iconSize = IconSize.SMALL, color = IconButtonColor.GHOST) {
                                    // Remove default href since we're wrapping in an anchor
                                    href = null
                                }
                                if (unreadNotificationCount > 0) {
                                    span("top-bar-notification__badge") {
                                        + unreadNotificationCount.toString()
                                    }
                                }
                            }
                        }
                        if (activeLinks.contains(ActiveLinks.PROFILE)) {
                            iconButton(Icons.Users.PROFILE, "Profile", iconSize = IconSize.SMALL) {
                                href = Link.Profile.to
                            }
                        }
                    }
                }
            }
        }
    }
}