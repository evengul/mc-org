package app.mcorg.presentation.templates

import io.ktor.util.*
import kotlinx.html.*

enum class MainPage {
    PROJECTS,
    USERS,
    WORLDS,
    PROFILE
}

fun mainPageTemplate(selectedPage: MainPage, worldId: Int?, title: String, rightIcons: List<NavBarRightIcon> = emptyList(), main: MAIN.() -> Unit) = pageTemplate(title, rightIcons, main) {
    script {
        src = "/static/scripts/menu.js"
        nonce = generateNonce()
    }
    button {
        classes = setOf("button-icon icon-menu")
        id = "menu-button"
    }
    menu(selectedPage, worldId)
}

fun subPageTemplate(title: String, backLink: String, rightIcons: List<NavBarRightIcon> = emptyList(), main: MAIN.() -> Unit) = pageTemplate(title, rightIcons, main) {
    a {
        href = backLink
        button {
            classes = setOf("button-icon icon-back")
            id = "back-button"
        }
    }
}

private fun pageTemplate(title: String, rightIcons: List<NavBarRightIcon>, main: MAIN.() -> Unit, leftIcon: NAV.() -> Unit) = baseTemplate(siteTitle = "MC-ORG | $title") {
    navBar(title, rightIcons, leftIcon)
    main {
        main()
    }
}