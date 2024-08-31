package app.mcorg.presentation.templates

import kotlinx.html.*

fun BODY.navBar(title: String, rightIcons: List<NavBarRightIcon>, leftIcon: NAV.() -> Unit) = nav {
    leftIcon()
    h1 {
        + title
    }
    if (rightIcons.isNotEmpty()) {
        if (rightIcons.size == 1) {
            a {
                href = rightIcons[0].link
                button {
                    + rightIcons[0].title
                }
            }
        } else {
            span {
                for (icon in rightIcons) {
                    a {
                        href = icon.link
                        button {
                            + icon.title
                        }
                    }
                }
            }
        }
    }
}

fun NAV.menu(worldId: Int?) = ul {
    if (worldId != null) {
        li {
            a {
                href = "/app/worlds/$worldId/projects"
                + "Projects"
            }
        }
        li {
            a {
                href = "/app/worlds/$worldId/users"
                + "Users"
            }
        }
        hr {  }
    }
    li {
        a {
            href = "/app/worlds"
            + "Worlds"
        }
    }
    li {
        a {
            href = "/app/profile"
            + "Profile"
        }
    }
}