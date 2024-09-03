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

fun NAV.menu(selectedPage: MainPage, worldId: Int?) = ul {
    id = "menu-links"
    classes = setOf("menu-invisible")
    button {
        id = "menu-close-button"
        + "Close"
    }
    if (worldId != null) {
        li {
            classes = getListItemClasses(MainPage.PROJECTS, selectedPage)
            a {
                id = "menu-project-link"
                href = "/app/worlds/$worldId/projects"
                + "Projects"
            }
        }
        li {
            classes = getListItemClasses(MainPage.USERS, selectedPage)
            a {
                id = "menu-users-link"
                href = "/app/worlds/$worldId/users"
                + "Users"
            }
        }
    }
    hr {  }
    li {
        classes = getListItemClasses(MainPage.WORLDS, selectedPage)
        a {
            id = "menu-worlds-link"
            href = "/app/worlds"
            + "Worlds"
        }
    }
    li {
        classes = getListItemClasses(MainPage.PROFILE, selectedPage)
        a {
            id = "menu-profile-link"
            href = "/app/profile"
            + "Profile"
        }
    }
}

fun getListItemClasses(link: MainPage, page: MainPage): Set<String> {
    val classes = mutableSetOf("menu-link")
    classes += if (link == MainPage.PROJECTS || link == MainPage.USERS) {
        setOf("top-link")
    } else {
        setOf("bottom-link")
    }

    if (link == page) {
        classes += setOf("menu-selected")
    }
    return classes
}