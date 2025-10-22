package app.mcorg.presentation.templated.common.breadcrumb

import app.mcorg.presentation.templated.common.icon.Icon
import app.mcorg.presentation.templated.common.link.Link

/**
 * Represents a single item in the breadcrumb navigation
 * @param label The text to display for this breadcrumb
 * @param link The link to navigate to (null for current page, which will be non-clickable)
 * @param icon Optional icon to display before the label
 */
data class BreadcrumbItem(
    val label: String,
    val link: Link?,
    val icon: Icon? = null
)

/**
 * Container for a list of breadcrumb items
 * @param items The list of breadcrumb items, typically ordered from root to current page
 */
data class Breadcrumbs(
    val items: List<BreadcrumbItem>
) {
    companion object {
        /**
         * Creates an empty breadcrumbs object
         */
        fun empty(): Breadcrumbs = Breadcrumbs(emptyList())
    }
}

