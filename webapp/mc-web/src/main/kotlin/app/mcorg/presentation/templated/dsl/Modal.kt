package app.mcorg.presentation.templated.dsl

import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTargetError
import kotlinx.html.*

const val CONFIRM_DELETE_MODAL_ID = "confirm-delete-modal"

fun BODY.confirmDeleteModal() {
    dialog {
        id = CONFIRM_DELETE_MODAL_ID
        classes = setOf("modal-backdrop")
        attributes["role"] = "dialog"
        attributes["aria-labelledby"] = "$CONFIRM_DELETE_MODAL_ID-title"
        attributes["aria-describedby"] = "$CONFIRM_DELETE_MODAL_ID-description"
        attributes["onclick"] = "if(event.target===this)this.close()"

        div("modal") {
            button(classes = "modal__close-btn") {
                type = ButtonType.button
                attributes["onclick"] = "window.closeDeleteConfirmModal()"
                +"×"
            }

            div("modal__heading") {
                id = "$CONFIRM_DELETE_MODAL_ID-title"
            }

            div("modal__body") {
                p {
                    id = "$CONFIRM_DELETE_MODAL_ID-description"
                    classes = setOf("modal__description")
                }

                div {
                    id = "$CONFIRM_DELETE_MODAL_ID-warning"
                    classes = setOf("modal__warning")
                }

                div {
                    id = "$CONFIRM_DELETE_MODAL_ID-input-container"
                    classes = setOf("is-hidden")

                    label {
                        htmlFor = "$CONFIRM_DELETE_MODAL_ID-input"
                        id = "$CONFIRM_DELETE_MODAL_ID-input-label"
                    }

                    input {
                        type = InputType.text
                        id = "$CONFIRM_DELETE_MODAL_ID-input"
                        attributes["autocomplete"] = "off"
                        attributes["oninput"] = "window.validateDeleteConfirmation()"
                    }

                    p {
                        id = "$CONFIRM_DELETE_MODAL_ID-validation-error"
                        classes = setOf("modal__error", "is-hidden")
                        +"Text does not match"
                    }
                }
            }

            div("modal__actions") {
                button(classes = "btn btn--secondary") {
                    type = ButtonType.button
                    attributes["onclick"] = "window.closeDeleteConfirmModal()"
                    +"Cancel"
                }
                button(classes = "btn btn--danger") {
                    id = "$CONFIRM_DELETE_MODAL_ID-confirm-btn"
                    type = ButtonType.button
                    disabled = true
                    attributes["onclick"] = "window.executeDeleteConfirmation()"
                    +"Delete"
                }
            }
        }
    }
}

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
                    attributes["hx-on::after-request"] =
                        "if(event.detail.successful) { this.reset(); this.closest('dialog')?.close() }"
                    body()
                }
            }
        }
    }
}
