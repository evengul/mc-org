package app.mcorg.presentation

import kotlinx.html.CommonAttributeGroupFacade
import kotlinx.html.FlowContent
import kotlinx.html.HTMLTag
import kotlinx.html.TR
import kotlinx.html.TagConsumer
import kotlinx.html.visit

/**
 * `<template>` wrapper for OOB swaps of bare table elements.
 *
 * Browsers strip orphan `<tr>`, `<td>`, `<tbody>`, etc. during HTML fragment parsing,
 * so an OOB `<tr>` at the top level of the response is silently discarded before HTMX
 * sees it. HTMX handles this by letting you wrap the element in a `<template>` — HTMX
 * unwraps the template on the client and processes the contained OOB element normally.
 *
 * kotlinx.html 0.12.0 does not expose a `<template>` tag, so this defines one.
 */
class TEMPLATE(
    initialAttributes: Map<String, String>,
    override val consumer: TagConsumer<*>,
) : HTMLTag(
    tagName = "template",
    consumer = consumer,
    initialAttributes = initialAttributes,
    namespace = null,
    inlineTag = false,
    emptyTag = false,
), CommonAttributeGroupFacade

/**
 * Emit a `<tr>` wrapped in a `<template>`, with `hx-swap-oob` set so HTMX unwraps it
 * and swaps the row into the target by id.
 *
 * Use this whenever you need to OOB-swap a `<tr>` — e.g. re-rendering a plan table
 * row as a side-effect of a response whose main target is elsewhere (a detail panel,
 * a modal, a summary card, …).
 *
 * The main response should still have a separate top-level target element for the
 * `hx-target` the request was sent to; this helper only emits the OOB sidecar.
 *
 * @param targetId id attribute of the row being replaced (without `#`)
 * @param swap HTMX swap strategy (defaults to `outerHTML`, which replaces the whole row)
 * @param block row content — the same `TR.() -> Unit` you'd pass to `tr { … }`
 */
fun FlowContent.oobTableRow(
    targetId: String,
    swap: String = "outerHTML",
    block: TR.() -> Unit,
) {
    TEMPLATE(emptyMap(), consumer).visit {
        TR(emptyMap(), consumer).visit {
            attributes["hx-swap-oob"] = "$swap:#$targetId"
            block()
        }
    }
}
