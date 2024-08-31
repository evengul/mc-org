package app.mcorg.presentation.templates

import kotlinx.html.*

fun mainPageTemplate(worldId: Int?, title: String, rightIcons: List<NavBarRightIcon> = emptyList(), main: MAIN.() -> Unit) = pageTemplate(title, rightIcons, main) {
    button {
        + "Menu"
    }
    menu(worldId)
}

fun subPageTemplate(title: String, backLink: String, rightIcons: List<NavBarRightIcon> = emptyList(), main: MAIN.() -> Unit) = pageTemplate(title, rightIcons, main) {
    a {
        href = backLink
        button {
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