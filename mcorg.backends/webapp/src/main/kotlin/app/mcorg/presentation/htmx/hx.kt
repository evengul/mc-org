package app.mcorg.presentation.htmx

import kotlinx.html.HTMLTag

fun HTMLTag.hxGet(value: String) {
    attributes += "hx-get" to value
}

fun HTMLTag.hxPut(value: String) {
    attributes += "hx-put" to value
}

fun HTMLTag.hxDelete(value: String) {
    attributes += "hx-delete" to value
}

fun HTMLTag.hxSwap(value: String) {
    attributes += "hx-swap" to value
}

fun HTMLTag.hxTarget(value: String) {
    attributes += "hx-target" to value
}

fun HTMLTag.hxConfirm(value: String) {
    attributes += "hx-confirm" to value
}