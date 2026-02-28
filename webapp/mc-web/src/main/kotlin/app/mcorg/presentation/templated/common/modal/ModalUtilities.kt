package app.mcorg.presentation.templated.common.modal

import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.NodeComponent
import kotlinx.html.TagConsumer
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.p

/**
 * A simple text content component for modals
 */
class ModalTextContent(
    private val text: String,
    private val cssClasses: Set<String> = setOf("modal-text")
) : LeafComponent() {

    override fun render(container: TagConsumer<*>) {
        container.p {
            classes = cssClasses
            +text
        }
    }
}

/**
 * A section wrapper for modal content
 */
class ModalSection(
    private val cssClasses: Set<String> = setOf("modal-section")
) : NodeComponent() {

    override fun render(container: TagConsumer<*>) {
        container.div {
            classes = cssClasses
            renderChildren(container)
        }
    }
}

/**
 * Extension functions to add common content types to modals
 */
fun Modal.addTextContent(text: String, cssClasses: Set<String> = setOf("modal-text")): Modal {
    addComponent(ModalTextContent(text, cssClasses))
    return this
}

fun Modal.addSection(cssClasses: Set<String> = setOf("modal-section"), block: ModalSection.() -> Unit = {}): Modal {
    val section = ModalSection(cssClasses)
    block.invoke(section)
    addComponent(section)
    return this
}

/**
 * Utility function to programmatically open a modal
 */
fun openModal(modalId: String): String = "document.getElementById('$modalId')?.showModal()"

/**
 * Utility function to programmatically close a modal
 */
fun closeModal(modalId: String): String = "document.getElementById('$modalId')?.close()"
