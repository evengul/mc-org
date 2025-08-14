package app.mcorg.presentation.templated.layout.topbar

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.templated.common.button.iconButton
import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.link.linkComponent
import kotlinx.html.BODY
import kotlinx.html.TagConsumer
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.header
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.ul

fun BODY.topBar(user: TokenProfile? = null) = addComponent(TopBar(user))

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
    val activeLinks: Set<ActiveLinks> = setOf(
        ActiveLinks.HOME,
        ActiveLinks.ADMIN_DASHBOARD,
        ActiveLinks.PROFILE
    ),
) : LeafComponent() {
    override fun render(container: TagConsumer<*>) {
        container.header {
            classes += "top-bar"
            h1 { +"MC-ORG" }
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
                    if (activeLinks.contains(ActiveLinks.IDEAS)) {
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
                    if (activeLinks.contains(ActiveLinks.ADMIN_DASHBOARD) && user?.isSuperAdmin == true) {
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
                        iconButton(Icons.Dimensions.OVERWORLD, iconSize = IconSize.SMALL)
                    }
                    if (user != null) {
                        if (activeLinks.contains(ActiveLinks.NOTIFICATIONS)) {
                            iconButton(Icons.Notification.INFO, iconSize = IconSize.SMALL)
                        }
                        if (activeLinks.contains(ActiveLinks.PROFILE)) {
                            iconButton(Icons.Users.PROFILE, iconSize = IconSize.SMALL) {
                                href = Link.Profile.to
                            }
                        }
                    }
                }
            }
        }
    }
}