package app.mcorg.presentation.templated.dsl

import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.p

fun FlowContent.dangerZone(
    title: String = "Danger Zone",
    description: String? = null,
    block: FlowContent.() -> Unit,
) {
    div("danger-zone") {
        p("danger-zone__title") { +title }
        description?.let { p("danger-zone__description subtle") { +it } }
        div("danger-zone__content") {
            block()
        }
    }
}
