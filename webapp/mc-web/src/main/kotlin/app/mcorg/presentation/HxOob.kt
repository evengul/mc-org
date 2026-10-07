package app.mcorg.presentation

import kotlinx.html.CommonAttributeGroupFacade
import kotlinx.html.FlowContent
import kotlinx.html.HTMLTag
import kotlinx.html.TagConsumer
import kotlinx.html.visit
import kotlinx.html.visitAndFinalize

/**
 * `<template>`, which kotlinx.html 0.12.0 does not offer outside phrasing content.
 *
 * htmx 4 reads one kind of template from a response: `<template hx type="partial">`, its
 * spelling of `<hx-partial>` ([hxPartial]). It does **not** look inside any other template for
 * `hx-swap-oob` elements, as htmx 2 did.
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
), CommonAttributeGroupFacade, FlowContent

private fun partialAttributes(target: String, swap: String) =
    mapOf("hx" to "", "type" to "partial", "hx-target" to target, "hx-swap" to swap)

/**
 * An htmx partial: [block] is swapped into [target] with [swap], alongside or instead of the
 * response's main content.
 *
 * Unlike `hx-swap-oob`, [target] is any selector, resolved from the element that sent the
 * request, so `find …`, `closest …` and `next …` work (field messages use `find`, `FieldError.kt`).
 * It is also how to send bare table rows (`tr`, `td`, …), which the parser would otherwise
 * drop: a template's content keeps them. A response made only of partials leaves the main
 * target alone, and partials are swapped whatever the status, so they also carry error output
 * past `noSwap` (`Layout.kt`).
 */
fun FlowContent.hxPartial(target: String, swap: String = "innerHTML", block: TEMPLATE.() -> Unit) {
    TEMPLATE(partialAttributes(target, swap), consumer).visit(block)
}

/** [hxPartial] as a whole response: `createHTML().hxPartial(…) { … }`. */
fun <T, C : TagConsumer<T>> C.hxPartial(target: String, swap: String = "innerHTML", block: TEMPLATE.() -> Unit): T =
    TEMPLATE(partialAttributes(target, swap), this).visitAndFinalize(this, block)
