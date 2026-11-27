package app.mcorg.presentation.templated.dsl

import kotlinx.html.BODY
import kotlinx.html.ButtonType
import kotlinx.html.LI
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.ul
import kotlinx.html.unsafe

const val ALERT_CONTAINER_ID = "alert-container"

fun BODY.alertContainer() {
    ul {
        id = ALERT_CONTAINER_ID
    }
}

enum class AlertType {
    SUCCESS,
    ERROR,
    WARNING,
    INFO
}

fun LI.createAlert(
    id: String,
    type: AlertType,
    title: String,
    message: String? = null,
    autoClose: Boolean = type == AlertType.INFO || type == AlertType.SUCCESS,
) {
    this.id = id
    classes = setOf("alert-dialog") + when (type) {
        AlertType.SUCCESS -> setOf("alert-dialog--success")
        AlertType.ERROR -> setOf("alert-dialog--error")
        AlertType.WARNING -> setOf("alert-dialog--warning")
        AlertType.INFO -> setOf("alert-dialog--info")
    } + when (autoClose) {
        true -> setOf("alert-dialog--auto-close")
        false -> emptySet()
    }

    if (autoClose) {
        script {
            unsafe {
                +"setTimeout(() => { document.getElementById('$id')?.remove() }, 3000);"
            }
        }
    }
    // A plain glyph button, like .modal__close-btn. iconButton would draw its 48px SVG in an
    // unstyled button, the largest thing in the toast.
    button(type = ButtonType.button, classes = "alert-dialog__close-button") {
        attributes["aria-label"] = "Dismiss"
        attributes["onclick"] = "document.getElementById('$id')?.remove()"
        +"×"
    }
    p {
        classes = setOf("alert-dialog__title")
        +title
    }
    message?.takeIf { it.isNotBlank() }?.let {
        p {
            classes = setOf("alert-dialog__message")
            +it
        }
    }
}
