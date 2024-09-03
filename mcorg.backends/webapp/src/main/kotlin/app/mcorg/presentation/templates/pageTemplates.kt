package app.mcorg.presentation.templates

import kotlinx.html.*

enum class MainPage {
    PROJECTS,
    USERS,
    WORLDS,
    PROFILE
}

fun mainPageTemplate(selectedPage: MainPage, worldId: Int?, title: String, rightIcons: List<NavBarRightIcon> = emptyList(), main: MAIN.() -> Unit) = pageTemplate(title, rightIcons, main) {
    script {
        src = "/static/menu.js"
    }
    button {
        id = "menu-button"
        + "Menu"
    }
    menu(selectedPage, worldId)
}

fun subPageTemplate(title: String, backLink: String, rightIcons: List<NavBarRightIcon> = emptyList(), main: MAIN.() -> Unit) = pageTemplate(title, rightIcons, main) {
    a {
        href = backLink
        button {
            id = "back-button"
            + "Back"
        }
    }
}

private fun pageTemplate(title: String, rightIcons: List<NavBarRightIcon>, main: MAIN.() -> Unit, leftIcon: NAV.() -> Unit) = baseTemplate(siteTitle = "MC-ORG | $title") {
    navBar(title, rightIcons, leftIcon)
    main {
        main()
    }
}