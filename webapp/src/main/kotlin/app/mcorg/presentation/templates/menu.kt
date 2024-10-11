package app.mcorg.presentation.templates

import kotlinx.html.*

fun BODY.navBar(title: String, rightIcons: List<NavBarRightIcon>, leftIcon: NAV.() -> Unit) = nav {
    leftIcon()
    h1 {
        + title
    }
    span {
        classes = setOf("navbar-links", "icon-row")
        for (icon in rightIcons) {
            if (icon.link != null) {
                a {
                    classes = setOf("navbar-link")
                    id = icon.icon + "-link"
                    href = icon.link
                    button {
                        classes = setOf("icon-row", "button-icon", "icon-${icon.icon}")
                    }
                }
            } else if (icon.id != null) {
                button {
                    id = icon.id
                    if (icon.onClick != null) {
                        onClick = icon.onClick
                    }
                    for ((name, value) in icon.data) {
                        attributes[name] = value
                    }
                    classes = setOf("icon-row", "button-icon", "icon-${icon.icon}")
                }
            } else {
                throw IllegalArgumentException("Icon must have either link or id")
            }
        }
    }
}

fun NAV.menu(selectedPage: MainPage, worldId: Int?) = ul {
    id = "menu-links"
    classes = setOf("menu", "menu-invisible")
    button {
        id = "menu-close-button"
        classes = setOf("button-icon", "icon-back")
    }
    if (worldId != null) {
        li {
            classes = getListItemClasses(MainPage.PROJECTS, selectedPage)
            a {
                classes = setOf("icon-row")
                id = "menu-project-link"
                href = "/app/worlds/$worldId/projects"
                span {
                    classes = setOf("icon", "icon-menu-projects")
                }
                h2 {
                    + "Projects"
                }
            }
        }
        li {
            classes = getListItemClasses(MainPage.USERS, selectedPage)
            a {
                classes = setOf("icon-row")
                id = "menu-users-link"
                href = "/app/worlds/$worldId/users"
                span {
                    classes = setOf("icon", "icon-menu-users")
                }
                h2 {
                    + "Users"
                }
            }
        }
        li {
            classes = getListItemClasses(MainPage.CONTRAPTIONS, selectedPage)
            a {
                classes = setOf("icon-row")
                id = "menu-contraptions-link"
                href = "/app/contraptions"
                span {
                    classes = setOf("icon", "icon-menu-contraptions")
                }
                h2 {
                    + "Contraptions"
                }
            }
        }
    }
    hr {  }
    li {
        classes = getListItemClasses(MainPage.WORLDS, selectedPage)
        a {
            classes = setOf("icon-row")
            id = "menu-worlds-link"
            href = "/app/worlds"
            span {
                classes = setOf("icon", "icon-menu-worlds")
            }
            h2 {
                + "Worlds"
            }
        }
    }
    li {
        classes = getListItemClasses(MainPage.PROFILE, selectedPage)
        a {
            classes = setOf("icon-row")
            id = "menu-profile-link"
            href = "/app/profile"
            span {
                classes = setOf("icon", "icon-menu-profile")
            }
            h2 {
                + "Profile"
            }
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