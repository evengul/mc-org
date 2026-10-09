package app.mcorg.presentation

import kotlinx.html.FlowContent
import kotlinx.html.HTMLTag
import kotlinx.html.div

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
    attributes += "hx-confirm" to title

    attributes += "data-hx-delete-confirm" to "true"
    attributes += "data-hx-delete-confirm-title" to title
    description?.let {
        attributes += "data-hx-delete-confirm-description" to it
    }
    warning?.let {
        attributes += "data-hx-delete-confirm-warning" to it
    }
    confirmText?.let {
        attributes += "data-hx-delete-confirm-text" to it
    }
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

fun HTMLTag.hxTrigger(value: String) {
    attributes += "hx-trigger" to value
}

fun HTMLTag.hxIndicator(value: String) {
    attributes += "hx-indicator" to value
}

fun HTMLTag.hxOutOfBands(locator: String) {
    attributes += "hx-swap-oob" to locator
}

/**
 * Runs [script] after this element's own request answered below 400, with `this` the element.
 *
 * htmx events bubble, so a form also hears the requests of the htmx elements inside it (an item
 * search, say); the target check keeps those from resetting or closing it. htmx 4 has no
 * `event.detail.successful`; the status is on `ctx`.
 */
fun HTMLTag.hxOnSuccess(script: String) {
    attributes += "hx-on::after:request" to "if (event.target === this && ctx.response.status < 400) { $script }"
}

fun HTMLTag.hxInclude(value: String) {
    attributes += "hx-include" to value
}

/**
 * A placeholder that fetches [url] with the page and is replaced by the response.
 *
 * It replaces *itself* (outerHTML) rather than filling a slot that keeps the request on it,
 * because htmx 4 runs here with `implicitInheritance` (`Layout.kt`): an element inherits its
 * ancestors' hx-get and hx-trigger. Content left inside an element carrying `hx-trigger="load"`
 * inherits both, so every control in the response fires that GET again on arrival, into its own
 * target. On the project page that replaced the whole plan with a picker, and with the bulk-answer
 * control (MCO-504). A slot that has to stay wraps this placeholder; it never carries the request.
 */
fun FlowContent.loadOnArrival(url: String) {
    div {
        hxGet(url)
        hxTrigger("load")
        hxTarget("this")
        hxSwap("outerHTML")
    }
}
