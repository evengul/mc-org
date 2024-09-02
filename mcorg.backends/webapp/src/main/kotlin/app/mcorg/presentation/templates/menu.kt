package app.mcorg.presentation.templates

import kotlinx.html.*

fun BODY.navBar(title: String, rightIcons: List<NavBarRightIcon>, leftIcon: NAV.() -> Unit) = nav {
    leftIcon()
    h1 {
        + title
    }
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

fun NAV.menu(worldId: Int?) = ul {
    id = "menu-links"
    classes = setOf("menu-invisible")
    button {
        id = "menu-close-button"
        + "Close"
    }
    if (worldId != null) {
        li {
            classes = setOf("menu-link", "menu-top-link")
            a {
                id = "menu-project-link"
                href = "/app/worlds/$worldId/projects"
                + "Projects"
            }
        }
        li {
            classes = setOf("menu-link", "menu-top-link")
            a {
                id = "menu-users-link"
                href = "/app/worlds/$worldId/users"
                + "Users"
            }
        }
    }
    li {
        classes = setOf("menu-link", "menu-bottom-link")
        a {
            id = "menu-worlds-link"
            href = "/app/worlds"
            + "Worlds"
        }
    }
    li {
        classes = setOf("menu-link", "menu-bottom-link")
        a {
            id = "menu-profile-link"
            href = "/app/profile"
            + "Profile"
        }
    }
}