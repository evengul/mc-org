package app.mcorg.presentation.templated.common.modal

import app.mcorg.presentation.templated.common.button.GenericButton
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
import kotlinx.html.h1
import kotlinx.html.p

fun <T : Tag> T.modal(
    modalId: String,
    title: String,
    description: String = "",
    openButtonBlock: GenericButton.() -> Unit = {},
    block: (Modal.() -> Unit)? = null,
) {
    val openButton = GenericButton(title).apply(openButtonBlock)
    val modal = Modal(title, modalId, description, openButton)

    block?.invoke(modal)
    addComponent(modal)
}

open class Modal(
    val title: String,
    val modalId: String,
    var description: String = "",
    var openButton: GenericButton,
) : NodeComponent() {

    init {
        openButton.onClick = "document.getElementById('$modalId')?.showModal()"
    }

    override fun render(container: TagConsumer<*>) {
        openButton.render(container)
        container.dialog {
            attributes["id"] = modalId
            attributes["class"] = "modal"

            renderHeader()
            renderContent()
            renderCloseButton()
        }
    }

    protected open fun DIALOG.renderHeader() {
        h1 {
            classes = setOf("modal-title")
            +title
        }
        if (description.isNotBlank()) {
            p("subtle") {
                classes = setOf("modal-description")
                +description
            }
        }
    }

    protected open fun DIALOG.renderContent() {
        renderChildren(this.consumer)
    }

    protected open fun DIALOG.renderCloseButton() {
        iconButton(Icons.CLOSE, iconSize = IconSize.SMALL) {
            onClick = "document.getElementById('$modalId')?.close()"
            addClass("modal-close-button")
        }
    }
}