package app.mcorg.presentation.templated.layout.topbar

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

fun BODY.topBar() = addComponent(TopBar())

class TopBar : LeafComponent() {
    override fun render(container: TagConsumer<*>) {
        container.header {
            classes += "top-bar"
            h1 { +"MC-ORG" }
            nav {
                classes += "top-bar-nav"
                ul {
                    classes += "top-bar-links"
                    li {
                        classes += "top-bar-link"
                        linkComponent(Link.Home) {
                            +"Home"
                        }
                    }
                    li {
                        classes += "top-bar-link"
                        linkComponent(Link.Ideas) {
                            + "Idea Bank"
                        }
                    }
                    li {
                        classes += "top-bar-link"
                        linkComponent(Link.Servers) {
                            + "Servers"
                        }
                    }
                }
                div {
                    classes += "top-bar-right"
                    iconButton(Icons.Dimensions.OVERWORLD, iconSize = IconSize.SMALL)
                    iconButton(Icons.Notification.INFO, iconSize = IconSize.SMALL)
                    iconButton(Icons.Users.PROFILE, iconSize = IconSize.SMALL)
                }
            }
        }
    }
}