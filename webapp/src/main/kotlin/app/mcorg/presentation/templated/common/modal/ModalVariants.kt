package app.mcorg.presentation.templated.common.modal

import app.mcorg.presentation.templated.common.button.GenericButton
import app.mcorg.presentation.templated.common.component.addComponent
import kotlinx.html.DIALOG
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div

/**
 * A confirmation modal with Yes/No buttons
 */
fun <T : Tag> T.confirmationModal(
    modalId: String,
    title: String,
    description: String,
    confirmText: String = "Yes",
    cancelText: String = "Cancel",
    onConfirm: String = "",
    onCancel: String = "document.getElementById('$modalId')?.close()",
    openButtonBlock: GenericButton.() -> Unit = {},
    block: (ConfirmationModal.() -> Unit) = {}
) {
    val modal = ConfirmationModal(
        title, modalId, description, confirmText, cancelText,
        onConfirm, onCancel, GenericButton(title).apply(openButtonBlock)
    )
    block.invoke(modal)
    addComponent(modal)
}

class ConfirmationModal(
    title: String,
    modalId: String,
    description: String,
    val confirmText: String,
    val cancelText: String,
    val onConfirm: String,
    val onCancel: String,
    openButton: GenericButton,
) : Modal(title, modalId, description, openButton) {

    override fun DIALOG.renderContent() {
        // Render any additional content from child components
        renderChildren(this.consumer)

        // Render action buttons
        div {
            classes = setOf("modal-actions")

            button {
                classes = setOf("btn-neutral", "modal-cancel-button")
                attributes["onclick"] = onCancel
                +cancelText
            }

            button {
                classes = setOf("btn-danger", "modal-confirm-button")
                attributes["onclick"] = "$onConfirm; document.getElementById('$modalId')?.close()"
                +confirmText
            }
        }
    }
}

/**
 * An information modal with just an OK button
 */
fun <T : Tag> T.infoModal(
    modalId: String,
    title: String,
    description: String,
    okText: String = "OK",
    openButtonBlock: GenericButton.() -> Unit = {},
    block: (InfoModal.() -> Unit) = {}
) {
    val modal = InfoModal(title, modalId, description, okText, GenericButton(title).apply(openButtonBlock))
    block.invoke(modal)
    addComponent(modal)
}

class InfoModal(
    title: String,
    modalId: String,
    description: String,
    val okText: String,
    openButton: GenericButton,
) : Modal(title, modalId, description, openButton) {

    override fun DIALOG.renderContent() {
        // Render any additional content from child components
        renderChildren(this.consumer)

        // Render OK button
        div {
            classes = setOf("modal-actions")

            button {
                classes = setOf("btn-action", "modal-ok-button")
                attributes["onclick"] = "document.getElementById('$modalId')?.close()"
                +okText
            }
        }
    }
}
