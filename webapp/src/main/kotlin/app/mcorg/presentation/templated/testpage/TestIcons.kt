package app.mcorg.presentation.templated.testpage

import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.icon.iconComponent
import kotlinx.html.MAIN
import kotlinx.html.b
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.p
import kotlinx.html.summary

fun MAIN.testIcons() {
    details {
        summary {
            + "Icons"
        }
        icons.forEach { (category, iconSet) ->
            div("icon-set") {
                h3 {
                    + category
                }
                b {
                    + "Small Icons"
                }
                div("icon-subset") {
                    iconSet.forEach {
                        div("named-icon") {
                            iconComponent(it, IconSize.SMALL)
                            p("icon-name") {
                                + " ${it.name}[SMALL] (${it.path(IconSize.SMALL)})"
                            }
                        }
                    }
                }
                b {
                    + "Medium Icons"
                }
                div("icon-subset") {
                    iconSet.forEach {
                        div("named-icon") {
                            iconComponent(it, IconSize.MEDIUM)
                            p("icon-name") {
                                + " ${it.name}[MEDIUM] (${it.path(IconSize.MEDIUM)})"
                            }
                        }
                    }
                }
            }
        }
    }
}

private val icons = mapOf(
    "Dimension Icons" to setOf(
        Icons.Dimensions.END,
        Icons.Dimensions.NETHER,
        Icons.Dimensions.OVERWORLD
    ),
    "Menu icons" to setOf(
        Icons.Menu.CONTRAPTIONS,
        Icons.Menu.PROJECTS,
        Icons.Menu.ROAD_MAP,
        Icons.Menu.UTILITIES
    ),
    "Notification Icons" to setOf(
        Icons.Notification.ERROR,
        Icons.Notification.WARNING,
        Icons.Notification.INFO,
    ),
    "Priority Icons" to setOf(
        Icons.Priority.HIGH,
        Icons.Priority.MEDIUM,
        Icons.Priority.LOW,
    ),
    "User Icons" to setOf(
        Icons.Users.ADD,
        Icons.Users.GROUP,
        Icons.Users.PROFILE
    ),
    "Other icons" to setOf(
        Icons.ADD_WORLD,
        Icons.BACK,
        Icons.CLOSE,
        Icons.CHECK,
        Icons.DELETE,
        Icons.FILTER_LIST,
        Icons.MENU,
        Icons.MENU_ADD,
        Icons.MICROSOFT_LOGO
    )
)