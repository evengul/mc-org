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
            span("app-header__logo") { +"MC-ORG" }

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
            span("app-header__world-name") {
                +(worldName ?: "MC-ORG")
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
