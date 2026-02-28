package app.mcorg.presentation.templated.common.modal

import app.mcorg.presentation.templated.common.button.ButtonSize
import app.mcorg.presentation.templated.common.button.IconButtonColor
import app.mcorg.presentation.templated.common.button.dangerButton
import app.mcorg.presentation.templated.common.button.iconButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import kotlinx.html.BODY
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.classes
import kotlinx.html.dialog
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p

const val CONFIRM_DELETE_MODAL_ID = "confirm-delete-modal"

/**
 * Renders a global confirmation modal for delete operations.
 * This modal is populated dynamically via JavaScript when a delete action is triggered.
 *
 * Supports two modes:
 * - Type-to-confirm: User must type exact text to enable delete button
 * - Simple confirm: User clicks confirm button directly
 */
fun BODY.confirmDeleteModal() {
    dialog {
        id = CONFIRM_DELETE_MODAL_ID
        classes = setOf("modal", "modal--sm")
        attributes["role"] = "dialog"
        attributes["aria-labelledby"] = "$CONFIRM_DELETE_MODAL_ID-title"
        attributes["aria-describedby"] = "$CONFIRM_DELETE_MODAL_ID-description"

        // Modal backdrop click handler
        attributes["onclick"] = """
            if (event.target === this) {
                this.close();
            }
        """.trimIndent()

        // Use a dialog element
        attributes["data-modal"] = "true"

        div {
            classes = setOf("modal__content")

            // Header
            div {
                classes = setOf("modal__header")

                div {
                    id = "$CONFIRM_DELETE_MODAL_ID-title"
                    classes = setOf("modal__title")
                    // Title populated by JavaScript
                }

                div {
                    id = "$CONFIRM_DELETE_MODAL_ID-description"
                    classes = setOf("modal__description", "u-text-subtle")
                    // Description populated by JavaScript
                }
            }

            // Body
            div {
                classes = setOf("modal__body")

                // Warning message
                div {
                    id = "$CONFIRM_DELETE_MODAL_ID-warning"
                    classes = setOf("notice", "notice--danger", "u-padding-xs")
                    // Warning message populated by JavaScript
                }

                // Type-to-confirm input (hidden by default, shown for type-to-confirm mode)
                div {
                    id = "$CONFIRM_DELETE_MODAL_ID-input-container"
                    classes = setOf("form-group")
                    attributes["style"] = "display: none;"

                    label {
                        htmlFor = "$CONFIRM_DELETE_MODAL_ID-input"
                        id = "$CONFIRM_DELETE_MODAL_ID-input-label"
                        // Label populated by JavaScript
                    }

                    input {
                        type = InputType.text
                        id = "$CONFIRM_DELETE_MODAL_ID-input"
                        classes = setOf("form-control")
                        attributes["autocomplete"] = "off"
                        attributes["oninput"] = "window.validateDeleteConfirmation()"
                    }

                    p {
                        id = "$CONFIRM_DELETE_MODAL_ID-validation-error"
                        classes = setOf("form-help", "u-text-danger")
                        attributes["style"] = "display: none;"
                        +"Text does not match"
                    }
                }
            }

            // Footer with action buttons
            div {
                classes = setOf("modal__footer")

                neutralButton("Cancel") {
                    onClick = "window.closeDeleteConfirmModal()"
                    buttonBlock = {
                        type = ButtonType.button
                    }
                }

                dangerButton("Delete") {
                    onClick = "window.executeDeleteConfirmation()"
                    buttonBlock = {
                        id = "$CONFIRM_DELETE_MODAL_ID-confirm-btn"
                        disabled = true
                        type = ButtonType.button
                    }
                }
            }

            // Close button
            iconButton(
                icon = Icons.CLOSE,
                ariaLabel = "Close",
                size = ButtonSize.MEDIUM,
                iconSize = IconSize.MEDIUM,
                color = IconButtonColor.NEUTRAL,
            ) {
                classes = setOf("modal__close", "u-cursor-pointer")
                onClick = "window.closeDeleteConfirmModal()"
            }
        }
    }
}

