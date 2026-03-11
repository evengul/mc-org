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

private fun gearHref(worldId: Int?, projectId: Int?): String? = when {
    projectId != null -> null
    worldId != null -> "/worlds/$worldId/settings"
    else -> "/profile"
}

fun FlowContent.appHeader(
    worldName: String? = null,
    worldId: Int? = null,
    projectId: Int? = null,
    user: TokenProfile? = null,
    breadcrumbBlock: (BreadcrumbBuilder.() -> BreadcrumbBuilder)? = null
) {
    val gear = gearHref(worldId, projectId)

    header("app-header") {
        div("app-header__desktop") {
            span("app-header__logo") { +"MC-ORG" }

            if (breadcrumbBlock != null) {
                val builder = BreadcrumbBuilder()
                builder.breadcrumbBlock()
                nav("breadcrumb") {
                    for ((index, segment) in builder.segments.withIndex()) {
                        if (index > 0) {
                            span("breadcrumb__sep") { +"\u203A" }
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
                if (gear != null) {
                    a(classes = "app-header__link") {
                        href = gear
                        attributes["aria-label"] = "Settings"
                        +"\u2699"
                    }
                }
            }
        }

        div("app-header__mobile") {
            span("app-header__world-name") {
                +(worldName ?: "MC-ORG")
            }
            if (gear != null) {
                a(classes = "app-header__link") {
                    href = gear
                    attributes["aria-label"] = "Settings"
                    +"\u2699"
                }
            }
        }
    }
}
