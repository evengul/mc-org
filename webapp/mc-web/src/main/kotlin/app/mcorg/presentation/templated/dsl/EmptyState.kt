package app.mcorg.presentation.templated.dsl

import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.p

fun FlowContent.emptyState(heading: String, body: String? = null, block: (FlowContent.() -> Unit)? = null) {
    div("empty-state") {
        h2("empty-state__heading") { +heading }
        if (body != null) {
            p("empty-state__body") { +body }
        }
        if (block != null) {
            div("empty-state__actions") { block() }
        }
    }
}

fun FlowContent.emptyStateCards(id: String? = null, block: FlowContent.() -> Unit) {
    div("empty-state-cards") {
        if (id != null) this.id = id
        block()
    }
}
