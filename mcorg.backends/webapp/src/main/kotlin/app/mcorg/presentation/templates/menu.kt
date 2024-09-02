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
                classes = setOf("navbar-link")
                id = rightIcons[0].icon + "-link"
                href = rightIcons[0].link
                button {
                    + rightIcons[0].title
                }
            }
        } else {
            span {
                classes = setOf("navbar-links")
                for (icon in rightIcons) {
                    a {
                        classes = setOf("navbar-link")
                        id = icon.icon + "-link"
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
    id = "menu-links"
    classes = setOf("invisible")
    if (worldId != null) {
        li {
            classes = setOf("menu-link")
            a {
                id = "menu-project-link"
                href = "/app/worlds/$worldId/projects"
                + "Projects"
            }
        }
        li {
            classes = setOf("menu-link-element")
            a {
                id = "menu-users-link"
                href = "/app/worlds/$worldId/users"
                + "Users"
            }
        }
        hr {  }
    }
    li {
        classes = setOf("menu-link")
        a {
            id = "menu-worlds-link"
            href = "/app/worlds"
            + "Worlds"
        }
    }
    li {
        classes = setOf("menu-link")
        a {
            id = "menu-profile-link"
            href = "/app/profile"
            + "Profile"
        }
    }
}