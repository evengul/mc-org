package app.mcorg.presentation.templated.dsl

import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTargetError
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

/**
 * Convenience wrapper that renders a modal with a form baked in.
 * The form submits via hx-post to [action], targeting [hxTarget] with [hxSwap] strategy.
 * Validation errors are routed to [errorTarget].
 */
fun FlowContent.modalForm(
    id: String,
    title: String,
    action: String,
    hxTarget: String,
    hxSwap: String = "outerHTML",
    errorTarget: String = ".form-error",
    body: FlowContent.() -> Unit
) {
    dialog {
        this.id = id
        classes = setOf("modal-backdrop")
        div("modal") {
            div("modal__heading") { +title }
            div("modal__body") {
                form {
                    hxPost(action)
                    hxTarget(hxTarget)
                    hxSwap(hxSwap)
                    hxTargetError(errorTarget)
                    body()
                }
            }
        }
    }
}
