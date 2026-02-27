package app.mcorg.presentation.templated.common.breadcrumb

import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.iconComponent
import kotlinx.html.*

/**
 * Renders a breadcrumb navigation component
 */
fun <T : Tag> T.breadcrumbComponent(breadcrumbs: Breadcrumbs) {
    if (breadcrumbs.items.isEmpty()) return

    val component = BreadcrumbComponent(breadcrumbs)
    addComponent(component)
}

class BreadcrumbComponent(
    private val breadcrumbs: Breadcrumbs
) : LeafComponent() {

    override fun render(container: TagConsumer<*>) {
        container.nav {
            classes = setOf("breadcrumb")
            attributes["aria-label"] = "Breadcrumb"

            ol {
                classes = setOf("breadcrumb__list")

                breadcrumbs.items.forEachIndexed { index, item ->
                    val isLast = index == breadcrumbs.items.size - 1

                    li {
                        classes = setOf("breadcrumb__item")
                        if (isLast) {
                            classes = classes + "breadcrumb__item--current"
                            attributes["aria-current"] = "page"
                        }

                        if (item.link != null && !isLast) {
                            // Clickable breadcrumb link
                            a(href = item.link.to) {
                                classes = setOf("breadcrumb__link")

                                item.icon?.let {
                                    iconComponent(it, size = IconSize.SMALL, classes = mutableSetOf("breadcrumb__icon"))
                                }

                                span("breadcrumb__label") {
                                    +item.label
                                }
                            }
                        } else {
                            // Current page (non-clickable)
                            span("breadcrumb__text") {
                                item.icon?.let { icon ->
                                    iconComponent(icon, size = IconSize.SMALL)
                                }

                                span("breadcrumb__label") {
                                    +item.label
                                }
                            }
                        }

                        // Add separator between items (except after last item)
                        if (!isLast) {
                            span("breadcrumb__separator") {
                                attributes["aria-hidden"] = "true"
                                +"/"
                            }
                        }
                    }
                }
            }
        }
    }
}

