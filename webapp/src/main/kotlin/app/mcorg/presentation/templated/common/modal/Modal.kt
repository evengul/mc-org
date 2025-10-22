package app.mcorg.presentation.templated.common.modal

import app.mcorg.presentation.templated.common.button.GenericButton
import app.mcorg.presentation.templated.common.button.IconButtonColor
import app.mcorg.presentation.templated.common.button.iconButton
import app.mcorg.presentation.templated.common.component.NodeComponent
import app.mcorg.presentation.templated.common.component.addComponent
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import kotlinx.html.DIALOG
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.classes
import kotlinx.html.dialog
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.p

enum class ModalSize {
    SMALL, MEDIUM, LARGE, EXTRA_LARGE
}

enum class ModalVariant {
    DEFAULT, CENTERED, FULLSCREEN
}

fun <T : Tag> T.modal(
    modalId: String,
    title: String,
    description: String = "",
    size: ModalSize = ModalSize.MEDIUM,
    variant: ModalVariant = ModalVariant.DEFAULT,
    openButtonBlock: GenericButton.() -> Unit = {},
    block: (Modal.() -> Unit)? = null,
) {
    val openButton = GenericButton(title).apply(openButtonBlock)
    val modal = Modal(title, modalId, description, openButton, size, variant)

    block?.invoke(modal)
    addComponent(modal)
}

open class Modal(
    val title: String,
    val modalId: String,
    var description: String = "",
    var openButton: GenericButton,
    var size: ModalSize = ModalSize.MEDIUM,
    var variant: ModalVariant = ModalVariant.DEFAULT,
    var classes: MutableSet<String> = mutableSetOf(),
) : NodeComponent() {

    init {
        openButton.onClick = "document.getElementById('$modalId')?.showModal()"
    }

    override fun render(container: TagConsumer<*>) {
        openButton.render(container)
        container.dialog {
            attributes["id"] = modalId

            // Apply base modal class and modifiers
            classes = this@Modal.classes + mutableSetOf("modal").apply {
                // Add size modifier classes
                when (this@Modal.size) {
                    ModalSize.SMALL -> add("modal--sm")
                    ModalSize.MEDIUM -> { /* Default size */ }
                    ModalSize.LARGE -> add("modal--lg")
                    ModalSize.EXTRA_LARGE -> add("modal--xl")
                }

                // Add variant modifier classes
                when (this@Modal.variant) {
                    ModalVariant.DEFAULT -> { /* Default variant */ }
                    ModalVariant.CENTERED -> add("modal--centered")
                    ModalVariant.FULLSCREEN -> add("modal--fullscreen")
                }
            }

            // Add ARIA attributes for accessibility
            attributes["role"] = "dialog"
            attributes["aria-labelledby"] = "$modalId-title"
            if (description.isNotBlank()) {
                attributes["aria-describedby"] = "$modalId-description"
            }

            // Modal backdrop click handler
            attributes["onclick"] = """
                if (event.target === this) {
                    this.querySelector('form')?.reset();
                    this.close();
                }
            """.trimIndent()

            // Modal content container
            div {
                classes = setOf("modal__content")

                this@dialog.renderHeader()
                this@dialog.renderContent()
                this@dialog.renderCloseButton()
            }
        }
    }

    protected open fun DIALOG.renderHeader() {
        div {
            classes = setOf("modal__header")

            h1 {
                classes = setOf("modal__title")
                attributes["id"] = "$modalId-title"
                +title
            }

            if (description.isNotBlank()) {
                p {
                    classes = setOf("modal__description", "u-text-subtle")
                    attributes["id"] = "$modalId-description"
                    +description
                }
            }
        }
    }

    protected open fun DIALOG.renderContent() {
        div {
            classes = setOf("modal__body")
            renderChildren(this.consumer)
        }
    }

    protected open fun DIALOG.renderCloseButton() {
        iconButton(Icons.CLOSE, "Close modal", iconSize = IconSize.SMALL, color = IconButtonColor.GHOST) {
            onClick = "this.closest('dialog')?.querySelector('form')?.reset(); document.getElementById('$modalId')?.close();"
            addClass("modal__close")
            addClass("u-cursor-pointer")
        }
    }
}

// Convenience functions for common modal patterns
fun <T : Tag> T.confirmModal(
    modalId: String,
    title: String,
    message: String,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    onConfirm: String = "",
    onCancel: String = "",
    variant: ModalVariant = ModalVariant.CENTERED,
    openButtonBlock: GenericButton.() -> Unit = {},
    block: (Modal.() -> Unit)? = null,
) {
    modal(modalId, title, message, ModalSize.SMALL, variant, openButtonBlock) {
        // Custom modal body content can be added via the block parameter
        block?.invoke(this)
    }
}

fun <T : Tag> T.infoModal(
    modalId: String,
    title: String,
    message: String,
    size: ModalSize = ModalSize.MEDIUM,
    variant: ModalVariant = ModalVariant.DEFAULT,
    openButtonBlock: GenericButton.() -> Unit = {},
    block: (Modal.() -> Unit)? = null,
) {
    modal(modalId, title, message, size, variant, openButtonBlock, block)
}
