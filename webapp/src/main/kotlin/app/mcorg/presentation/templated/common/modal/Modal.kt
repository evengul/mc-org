package app.mcorg.presentation.templated.common.modal

import app.mcorg.presentation.templated.common.button.GenericButton
import app.mcorg.presentation.templated.common.button.iconButton
import app.mcorg.presentation.templated.common.component.NodeComponent
import app.mcorg.presentation.templated.common.component.addComponent
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import kotlinx.html.ButtonType
import kotlinx.html.DIALOG
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.dialog
import kotlinx.html.h1
import kotlinx.html.p

fun <T : Tag> T.modal(
    id: String,
    title: String,
    openButtonHandler: GenericButton.() -> Unit = {},
    handler: (Modal.() -> Unit)? = null,
) {
    val openButton = GenericButton(title).apply(openButtonHandler)
    openButton.onClick = "document.getElementById('$id')?.showModal()"
    val component = Modal(title, id, openButton = openButton)
    handler?.invoke(component)
    addComponent(component)
}

data class Modal(
    val title: String,
    val id: String,
    var description: String = "",
    var saveText: String = "Save and close",
    var openButton: GenericButton,
    var addChildren: DIALOG.() -> Unit = {},
) : NodeComponent() {
    override fun render(container: TagConsumer<*>) {
        openButton.render(container)
        container.dialog {
            attributes["id"] = id
            h1 {
                + title
            }
            if (description.isNotBlank()) {
                p {
                    classes = setOf("modal-description")
                    + description
                }
            }
            iconButton(Icons.CLOSE, iconSize = IconSize.SMALL) {
                onClick = "document.getElementById('$id')?.close()"
                addClass("modal-close-button")
            }
            addChildren()
            button {
                classes = setOf("modal-save-button")
                type = ButtonType.submit
                + saveText
            }
        }
    }
}