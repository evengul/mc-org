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

fun HTMLTag.hxTargetError(value: String) {
    attributes += "hx-target-error" to value
}

fun HTMLTag.hxOutOfBands(locator: String) {
    attributes += "hx-swap-oob" to locator
}

fun HTMLTag.hxConfirm(value: String) {
    attributes += "hx-confirm" to value
}

fun HTMLTag.hxExtension(value: String) {
    attributes += "hx-ext" to value
}