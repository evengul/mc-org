package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.user.TokenProfile
import kotlinx.html.*

class BreadcrumbBuilder {
    internal val segments = mutableListOf<BreadcrumbSegment>()

    fun breadcrumb(label: String, href: String): BreadcrumbBuilder {
        segments.add(BreadcrumbSegment.Link(label, href))
        return this
    }

    fun link(label: String, href: String): BreadcrumbBuilder {
        segments.add(BreadcrumbSegment.Link(label, href))
        return this
    }

    fun current(label: String): BreadcrumbBuilder {
        segments.add(BreadcrumbSegment.Current(label))
        return this
    }

    operator fun div(next: BreadcrumbBuilder): BreadcrumbBuilder {
        segments.addAll(next.segments)
        return this
    }
}

sealed class BreadcrumbSegment {
    data class Link(val label: String, val href: String) : BreadcrumbSegment()
    data class Current(val label: String) : BreadcrumbSegment()
}

private fun settingsHref(worldId: Int?, projectId: Int?, isWorldAdmin: Boolean): String? = when {
    projectId != null -> null
    worldId != null && isWorldAdmin -> "/worlds/$worldId/settings"
    else -> null
}

/**
 * World-level sections. A world opens on its roadmap — "what do I do next" — and the project
 * list is the reference view you go to deliberately (MCO-474).
 *
 * Rendered on *both* pages on purpose. `/worlds/{id}` was a **permanent** redirect to
 * `/projects` for a long time, and browsers cache a 301 indefinitely, so a browser that has
 * visited a world before will keep landing on the project list no matter what the server now
 * says. Putting the tabs on both leaves that browser one click from the roadmap.
 */
fun FlowContent.worldTabs(worldId: Int, active: WorldTab) {
    nav("world-tabs") {
        attributes["aria-label"] = "World sections"
        for (tab in WorldTab.entries) {
            val isActive = tab == active
            a(classes = if (isActive) "world-tabs__tab world-tabs__tab--active" else "world-tabs__tab") {
                href = "/worlds/$worldId${tab.path}"
                if (isActive) attributes["aria-current"] = "page"
                +tab.label
            }
        }
    }
}

/**
 * The world bar: sections on the left, the world's primary action on the right.
 *
 * "+ New project" lives here rather than in the project list's own toolbar (MCO-474) so it is
 * reachable from the roadmap — which is now what a world opens to, and was the one view with no
 * way to add anything.
 */
fun FlowContent.worldBar(worldId: Int, active: WorldTab, actions: (FlowContent.() -> Unit)? = null) {
    div("world-bar") {
        worldTabs(worldId, active)
        if (actions != null) {
            div("world-bar__actions") { actions() }
        }
    }
}

enum class WorldTab(val label: String, val path: String) {
    ROADMAP("Roadmap", "/roadmap"),
    PROJECTS("Projects", "/projects"),
}

fun FlowContent.appHeader(
    worldName: String? = null,
    worldId: Int? = null,
    projectId: Int? = null,
    user: TokenProfile? = null,
    isWorldAdmin: Boolean = false,
    breadcrumbBlock: (BreadcrumbBuilder.() -> BreadcrumbBuilder)? = null
) {
    val settings = settingsHref(worldId, projectId, isWorldAdmin)
    val showProfile = user != null

    header("app-header") {
        div("app-header__desktop") {
            a(classes = "app-header__logo") {
                href = "/"
                attributes["aria-label"] = "Seam — home"
                img(alt = "Seam", src = "/static/seam-lockup-horizontal.svg", classes = "app-header__logo-img") {
                    attributes["width"] = "111"
                    attributes["height"] = "40"
                }
            }

            if (breadcrumbBlock != null) {
                val builder = BreadcrumbBuilder()
                builder.breadcrumbBlock()
                nav("breadcrumb") {
                    for ((index, segment) in builder.segments.withIndex()) {
                        if (index > 0) {
                            span("breadcrumb__sep") { +"›" }
                        }
                        when (segment) {
                            is BreadcrumbSegment.Link -> a(classes = "breadcrumb__item") {
                                href = segment.href
                                +segment.label
                            }
                            is BreadcrumbSegment.Current -> span("breadcrumb__item breadcrumb__item--current") {
                                +segment.label
                            }
                        }
                    }
                }
            }

            div("app-header__actions") {
                a(classes = "app-header__link") {
                    href = "/ideas"
                    +"Ideas"
                }
                if (showProfile) {
                    a(classes = "app-header__link") {
                        href = "/profile"
                        +"Profile"
                    }
                }
                if (settings != null) {
                    a(classes = "app-header__link") {
                        href = settings
                        attributes["aria-label"] = "World settings"
                        +"⚙"
                    }
                }
            }
        }

        div("app-header__mobile") {
            // The mobile header has no breadcrumb, so without this there is no way back out
            // of a world's sub-pages at all. Linking the world name is the only anchor the
            // narrow layout has room for.
            if (worldId != null) {
                a(classes = "app-header__world-name app-header__world-name--link") {
                    href = "/worlds/$worldId"
                    +(worldName ?: "Seam")
                }
            } else {
                span("app-header__world-name") {
                    +(worldName ?: "Seam")
                }
            }
            if (showProfile) {
                a(classes = "app-header__link") {
                    href = "/profile"
                    +"Profile"
                }
            }
            if (settings != null) {
                a(classes = "app-header__link") {
                    href = settings
                    attributes["aria-label"] = "World settings"
                    +"⚙"
                }
            }
        }
    }
}
