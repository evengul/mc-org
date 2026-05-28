package app.mcorg.presentation.templated.dsl

import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.p
import kotlinx.html.section

fun FlowContent.section(
    title: String? = null,
    subtitle: String? = null,
    eyebrow: String? = null,
    tight: Boolean = false,
    card: Boolean = false,
    block: DIV.() -> Unit,
) {
    val sectionClasses = buildString {
        append("section")
        if (tight) append(" section--tight")
    }
    val bodyClasses = buildString {
        append("section__body")
        if (card) append(" section__card")
    }
    section(sectionClasses) {
        if (title != null || subtitle != null || eyebrow != null) {
            div("section__heading") {
                eyebrow?.let { p("section-label") { +it } }
                title?.let { h2("section__heading-title") { +it } }
                subtitle?.let { p("section__subtitle") { +it } }
            }
        }
        div(bodyClasses) {
            block()
        }
    }
}
