package app.mcorg.presentation

import kotlinx.html.HTMLTag

fun HTMLTag.hxGet(value: String) {
    attributes += "hx-get" to value
}

fun HTMLTag.hxPost(value: String) {
    attributes += "hx-post" to value
}

fun HTMLTag.hxPut(value: String) {
    attributes += "hx-put" to value
}

fun HTMLTag.hxPatch(value: String) {
    attributes += "hx-patch" to value
}

fun HTMLTag.hxDelete(value: String, confirmMessage: String) {
    attributes += "hx-delete" to value
    hxConfirm(confirmMessage)
}

/**
 * Configure delete action with custom confirmation modal
 * @param url The DELETE endpoint URL
 * @param title Modal title
 * @param description Modal description
 * @param warning Warning message shown in danger notice
 * @param confirmText Optional text user must type to confirm (enables type-to-confirm mode)
 */
fun HTMLTag.hxDeleteWithConfirm(
    url: String,
    title: String,
    description: String? = null,
    warning: String? = null,
    confirmText: String? = null,
) {
    attributes += "hx-delete" to url
    attributes += "hx-confirm" to title.escapeQuotes()

    attributes += "data-hx-delete-confirm" to "true"
    attributes += "data-hx-delete-confirm-title" to title.escapeQuotes()
    description?.let {
        attributes += "data-hx-delete-confirm-description" to it.escapeQuotes()
    }
    warning?.let {
        attributes += "data-hx-delete-confirm-warning" to it.escapeQuotes()
    }
    confirmText?.let {
        attributes += "data-hx-delete-confirm-text" to it.escapeQuotes()
    }
}

private fun String.escapeQuotes(): String = this.replace("'", "\\'")


fun HTMLTag.hxSwap(value: String) {
    attributes += "hx-swap" to value
}

fun HTMLTag.hxTarget(value: String) {
    attributes += "hx-target" to value
}

fun HTMLTag.hxErrorTarget(target: String, errorCode: String) {
    attributes += "hx-target-$errorCode" to target
}

fun HTMLTag.hxTrigger(value: String) {
    attributes += "hx-trigger" to value
}

fun HTMLTag.hxIndicator(value: String) {
    attributes += "hx-indicator" to value
}

fun HTMLTag.hxTargetError(value: String) {
    attributes += "hx-target-error" to value
}

fun HTMLTag.hxOutOfBands(locator: String) {
    attributes += "hx-swap-oob" to locator
}

fun HTMLTag.hxConfirm(value: String) {
    attributes += "hx-confirm" to value
}

fun HTMLTag.hxInclude(value: String) {
    attributes += "hx-include" to value
}

fun HTMLTag.hxExtension(value: String) {
    attributes += "hx-ext" to value
}