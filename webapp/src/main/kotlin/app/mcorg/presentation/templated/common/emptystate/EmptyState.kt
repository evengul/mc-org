package app.mcorg.presentation.templated.common.emptystate

import app.mcorg.presentation.templated.common.component.addComponent
import app.mcorg.presentation.templated.common.icon.Icon
import app.mcorg.presentation.templated.common.icon.IconColor
import app.mcorg.presentation.templated.common.icon.IconSize
import kotlinx.html.DIV
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.p

/**
 * Reusable empty state component for consistent UX across the application.
 *
 * Displays a centered message with optional icon and action buttons when content is empty.
 *
 * @param id Unique identifier for the empty state container
 * @param title The main heading (e.g., "No Projects Yet")
 * @param description Explanation text to help users understand the situation
 * @param icon Optional icon to display above the title for visual hierarchy
 * @param iconSize Size of the icon (default: MEDIUM)
 * @param iconColor Color of the icon (default: SUBTLE)
 * @param variant Layout variant: "default" (full centered), "inline" (fits within content), "compact" (minimal spacing)
 * @param useH2 If true, uses h2 for title; otherwise uses h3 (default: true)
 * @param actionBlock Optional lambda to add action buttons or other interactive elements
 */
fun DIV.emptyState(
    id: String,
    title: String,
    description: String,
    icon: Icon? = null,
    iconSize: IconSize = IconSize.MEDIUM,
    iconColor: IconColor = IconColor.SUBTLE,
    variant: EmptyStateVariant = EmptyStateVariant.DEFAULT,
    useH2: Boolean = true,
    actionBlock: (DIV.() -> Unit)? = null
) {
    div(classes = "empty-state ${variant.cssClass}") {
        this.id = id
        // Optional icon
        icon?.let {
            div("empty-state__icon") {
                addComponent(when(iconSize) {
                    IconSize.SMALL -> it.small(iconColor)
                    IconSize.MEDIUM -> it.medium(iconColor)
                    IconSize.LARGE -> it.large(iconColor)
                })
            }
        }

        // Title
        if (useH2) {
            h2("empty-state__title") {
                +title
            }
        } else {
            h3("empty-state__title") {
                +title
            }
        }

        // Description
        p("empty-state__description") {
            +description
        }

        // Optional actions
        actionBlock?.let {
            div("empty-state__actions") {
                it()
            }
        }
    }
}

/**
 * Empty state layout variants
 */
enum class EmptyStateVariant(val cssClass: String) {
    /**
     * Default: Full centered layout for page-level empty states
     */
    DEFAULT(""),

    /**
     * Inline: Fits within existing content areas without max-width constraints
     */
    INLINE("empty-state--inline"),

    /**
     * Compact: Less padding and smaller gaps for tight spaces
     */
    COMPACT("empty-state--compact")
}

