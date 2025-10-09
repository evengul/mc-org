package app.mcorg.presentation.templated.testpage

import app.mcorg.presentation.templated.common.icon.IconColor
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.icon.iconComponent
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.MAIN
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.h4
import kotlinx.html.h5
import kotlinx.html.style
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
                IconSize.entries.forEach { size ->
                    div("icon-size") {
                        h4 {
                            + "${size.toPrettyEnumName()} Icons"
                        }
                        IconColor.entries.forEach { color ->
                            div("icon-color") {
                                h5 {
                                    + "${color.toPrettyEnumName()} Icons"
                                }
                                iconSet.forEach { icon ->
                                    div {
                                        style = "background-color: ${color.toBackgroundColor()}"
                                        iconComponent(
                                            icon = icon,
                                            size = size,
                                            color = color
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun IconColor.toBackgroundColor(): String {
    return when (this) {
        IconColor.ACTION -> "var(--clr-bg-default)"
        IconColor.ON_ACTION -> "var(--clr-action)"
        IconColor.ON_NEUTRAL -> "var(--clr-neutral)"
        IconColor.ON_DANGER -> "var(--clr-danger)"
        IconColor.ON_SUCCESS -> "var(--clr-success)"
        IconColor.ON_WARNING -> "var(--clr-warning)"
        IconColor.ON_INFO -> "var(--clr-info)"
        IconColor.DEFAULT -> "var(--clr-bg-default)"
        IconColor.SUBTLE -> "var(--clr-bg-default)"
        IconColor.ACTION_TEXT -> "var(--clr-bg-default)"
        IconColor.DANGER_TEXT -> "var(--clr-bg-default)"
        IconColor.ON_BACKGROUND -> "var(--clr-bg-default)"
        IconColor.ON_SURFACE -> "var(--clr-bg-subtle)"
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