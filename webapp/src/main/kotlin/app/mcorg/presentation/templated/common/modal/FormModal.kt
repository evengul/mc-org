package app.mcorg.presentation.templated.common.modal

import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxPut
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.common.button.GenericButton
import app.mcorg.presentation.templated.common.component.addComponent
import kotlinx.html.ButtonType
import kotlinx.html.DIALOG
import kotlinx.html.FORM
import kotlinx.html.FormEncType
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.form
import kotlinx.html.id

enum class FormModalHttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH
}

data class FormModalHxValues(
    val hxTarget: String,
    val method: FormModalHttpMethod,
    val href: String
)

fun <T : Tag> T.formModal(
    modalId: String,
    title: String,
    description: String = "",
    saveText: String = "Save and close",
    hxValues: FormModalHxValues? = null,
    openButtonBlock: GenericButton.() -> Unit = {},
    block: (FormModal.() -> Unit) = {}
) {
    val modal = FormModal(title, modalId, description, saveText, hxValues, GenericButton(title).apply(openButtonBlock))
    block.invoke(modal)
    addComponent(modal)
}

class FormModal(
    title: String,
    modalId: String,
    description: String = "",
    val saveText: String = "Save and close",
    val hxValues: FormModalHxValues? = null,
    openButton: GenericButton,
) : Modal(title, modalId, description, openButton) {

    private var formContent: (FORM.() -> Unit)? = null

    fun formContent(block: FORM.() -> Unit) {
        this.formContent = block
    }

    override fun DIALOG.renderContent() {
        form {
            id = modalId.replace("-modal", "-form")
            attributes["class"] = "modal-form"
            encType = FormEncType.applicationXWwwFormUrlEncoded

            hxValues?.let { hx ->
                hxTarget(hx.hxTarget)
                when(hx.method) {
                    FormModalHttpMethod.GET -> hxGet(hx.href)
                    FormModalHttpMethod.POST -> hxPost(hx.href)
                    FormModalHttpMethod.PUT -> hxPut(hx.href)
                    FormModalHttpMethod.DELETE -> hxDelete(hx.href)
                    FormModalHttpMethod.PATCH -> hxPatch(hx.href)
                }
            }

            // render form fields from child components
            renderChildren(this.consumer)

            // render custom form content if provided
            formContent?.invoke(this)

            // render submit button
            button {
                type = ButtonType.submit
                classes = setOf("btn--action", "modal-submit-button")
                + saveText
            }
        }
    }
}
