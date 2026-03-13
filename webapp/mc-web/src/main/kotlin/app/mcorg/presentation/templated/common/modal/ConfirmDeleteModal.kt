package app.mcorg.presentation.templated.common.modal

import kotlinx.html.BODY
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.dialog
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p

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
                    attributes["style"] = "display: none;"

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
                        classes = setOf("modal__error")
                        attributes["style"] = "display: none;"
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
