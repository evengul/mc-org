package app.mcorg.presentation.templated.common.icon

import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.unsafe

enum class IconSize(val width: Int, val height: Int) {
    SMALL(24, 24),
    MEDIUM(48, 48),
    LARGE(64, 64)
}


/**
 * Icon colors using CSS custom properties instead of hardcoded hex values.
 * These align with the new CSS architecture documented in DOCUMENTATION.md
 */
enum class IconColor(val cssVar: String) {
    // Action/Primary colors
    ACTION("var(--clr-action)"),
    ON_ACTION("var(--clr-text-on-action)"),

    // State colors
    ON_NEUTRAL("var(--clr-text-on-neutral)"),
    ON_DANGER("var(--clr-text-on-danger)"),
    ON_SUCCESS("var(--clr-text-on-success)"),
    ON_WARNING("var(--clr-text-on-warning)"),
    ON_INFO("var(--clr-text-on-info)"),

    // Text colors
    DEFAULT("var(--clr-text-default)"),
    SUBTLE("var(--clr-text-subtle)"),
    ACTION_TEXT("var(--clr-text-action)"),
    DANGER_TEXT("var(--clr-text-danger)"),

    // Surface colors
    ON_BACKGROUND("var(--clr-text-default)"),
    ON_SURFACE("var(--clr-text-default)")
}

fun <T : Tag> T.iconComponent(
    icon: Icon,
    size: IconSize = IconSize.MEDIUM,
    color: IconColor = IconColor.DEFAULT,
    classes: MutableSet<String> = mutableSetOf()
) {
    val component = IconComponent(icon, size, color, classes)
    addComponent(component)
}

class IconComponent(
    private val icon: Icon,
    private val size: IconSize = IconSize.MEDIUM,
    private val color: IconColor = IconColor.DEFAULT,
    private val additionalClasses: MutableSet<String> = mutableSetOf(),
) : LeafComponent() {

    override fun render(container: TagConsumer<*>) {
        container.div {
            // Apply base icon class and modifiers
            classes = additionalClasses + mutableSetOf("icon").apply {
                // Add size modifier classes
                when (this@IconComponent.size) {
                    IconSize.SMALL -> add("icon--sm")
                    IconSize.MEDIUM -> add("icon--md")
                    IconSize.LARGE -> add("icon--lg")
                }

                // Add color class if not default
                if (color != IconColor.DEFAULT) {
                    add("icon--colored")
                }
            }

            // Set CSS custom property for icon color
            attributes["style"] = "color: ${color.cssVar};"

            // Render the SVG content with color replacement
            unsafe {
                + icon.readContent(this@IconComponent.size)
            }
        }
    }
}

class Icon(
    val name: String,
    val subPath: String = "",
) {

    fun small(color: IconColor = IconColor.DEFAULT) = IconComponent(this, IconSize.SMALL, color)

    fun medium(color: IconColor = IconColor.DEFAULT) = IconComponent(this, IconSize.MEDIUM, color)

    fun large(color: IconColor = IconColor.DEFAULT) = IconComponent(this, IconSize.LARGE, color)

    fun readContent(size: IconSize): String {
        // Read the base SVG file (without size suffix) and dynamically set dimensions
        val baseSvg = this::class.java.getResourceAsStream(basePath())?.bufferedReader()?.use { it.readText() }
            ?: UNKNOWN_ICON_SVG

        return baseSvg.setDimensions(size)
    }

    private fun String.setDimensions(size: IconSize): String {
        // Update width and height attributes to match the IconSize enum values
        return this.replace(Regex(""" width="[^"]*""""), """ width="${size.width}px"""")
            .replace(Regex(""" height="[^"]*""""), """ height="${size.height}px"""")
            .let { svg ->
                // Ensure viewBox is properly set if missing
                if (!svg.contains("viewBox")) {
                    svg.replace("<svg", """<svg viewBox="0 0 ${size.width} ${size.height}"""")
                } else {
                    svg
                }
            }
    }

    fun path(size: IconSize): String {
        // Legacy method - kept for backward compatibility
        return "/static/icons/${sanitizedSubPath()}${if (sanitizedSubPath().isEmpty()) "" else "/"}${name}_${size.width}x${size.height}.svg"
    }

    private fun basePath(): String {
        // New method - reads the base SVG file without size suffix
        return "/static/icons/${sanitizedSubPath()}${if (sanitizedSubPath().isEmpty()) "" else "/"}${name}_48x48.svg"
    }

    private fun sanitizedSubPath(): String {
        return subPath.removePrefix("/").removeSuffix("/")
    }
}

object Icons {
    object Dimensions {
        val END = Icon("End", "dimensions")
        val NETHER = Icon("Nether", "dimensions")
        val OVERWORLD = Icon("Overworld", "dimensions")
    }
    object Menu {
        val CONTRAPTIONS = Icon("Contraptions", "menu")
        val PROJECTS = Icon("Projects", "menu")
        val ROAD_MAP = Icon("Roadmap", "menu")
        val UTILITIES = Icon("Utilities", "menu")
    }
    object Notification {
        val ERROR = Icon("Error", "notification")
        val WARNING = Icon("Warning", "notification")
        val INFO = Icon("Info", "notification")
    }
    object Priority {
        val HIGH = Icon("High", "priority")
        val MEDIUM = Icon("Medium", "priority")
        val LOW = Icon("Low", "priority")
    }
    object Users {
        val ADD = Icon("Add", "users")
        val GROUP = Icon("Group", "users")
        val PROFILE = Icon("Profile", "users")
    }
    val ADD_WORLD = Icon("Add_World")
    val BACK = Icon("Back")
    val CLOSE = Icon("Close")
    val CHECK = Icon("Check")
    val DELETE = Icon("Delete")
    val FILTER_LIST = Icon("Filter_List")
    val MENU = Icon("Menu")
    val MENU_ADD = Icon("Menu_Add")
    val MICROSOFT_LOGO = Icon("Microsoft_Logo")
}

private val UNKNOWN_ICON_SVG = """
    <svg 
        xmlns="http://www.w3.org/2000/svg" 
        height="24px" 
        viewBox="0 -960 960 960" 
        width="24px" 
        fill="currentColor">
            <path d="M424-320q0-81 14.5-116.5T500-514q41-36 62.5-62.5T584-637q0-41-27.5-68T480-732q-51 0-77.5 31T365-638l-103-44q21-64 77-111t141-47q105 0 161.5 58.5T698-641q0 50-21.5 85.5T609-475q-49 47-59.5 71.5T539-320H424Zm56 240q-33 0-56.5-23.5T400-160q0-33 23.5-56.5T480-240q33 0 56.5 23.5T560-160q0 33-23.5 56.5T480-80Z"/>
    </svg>
""".trimIndent()