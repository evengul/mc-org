package app.mcorg.presentation.templated.dsl

import kotlinx.html.*

fun FlowContent.modal(id: String, title: String, body: FlowContent.() -> Unit) {
    dialog {
        this.id = id
        classes = setOf("modal-backdrop")
        div("modal") {
            div("modal__heading") { +title }
            div("modal__body") { body() }
        }
    }
}

fun FlowContent.modalActions(block: FlowContent.() -> Unit) {
    div("modal__actions") { block() }
}
