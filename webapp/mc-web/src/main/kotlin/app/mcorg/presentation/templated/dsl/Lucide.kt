package app.mcorg.presentation.templated.dsl

import kotlinx.html.FlowContent

/**
 * Inline [Lucide](https://lucide.dev) line icon, emitted as a bare `<svg class="icon">`
 * (no wrapper) so it sits directly as a flex child where the layout CSS expects it.
 * Colour/stroke come from the `.icon` class (see pages/worlds.css); `filled = true` adds
 * `.icon--fill` for solid glyphs (e.g. a pinned star). Sized in px per call because the
 * design uses fine-grained icon sizes (13–34px) that the fixed IconSize enum can't express.
 *
 * This is deliberately separate from [Icons]/[iconComponent], which render the app's own
 * Material-style SVG assets wrapped in a `div.icon`.
 */
fun FlowContent.lucide(
    name: String,
    size: Int = 16,
    filled: Boolean = false,
    vararg extraClasses: String,
) {
    val classes = buildList {
        add("icon")
        if (filled) add("icon--fill")
        addAll(extraClasses)
    }.joinToString(" ")

    val body = lucidePath(name)
    consumer.onTagContentUnsafe {
        +"""<svg class="$classes" width="${size}px" height="${size}px" viewBox="0 0 24 24" aria-hidden="true">$body</svg>"""
    }
}

private fun lucidePath(name: String): String = when (name) {
    "box" -> """<path d="M21 8v8a2 2 0 0 1-1 1.73l-7 4a2 2 0 0 1-2 0l-7-4A2 2 0 0 1 3 16V8a2 2 0 0 1 1-1.73l7-4a2 2 0 0 1 2 0l7 4A2 2 0 0 1 21 8z"/><path d="m3.3 7 8.7 5 8.7-5"/><path d="M12 22V12"/>"""
    "plus" -> """<path d="M12 5v14M5 12h14"/>"""
    "arrow-right" -> """<path d="M5 12h14M13 6l6 6-6 6"/>"""
    "layers" -> """<path d="M12 2 2 7l10 5 10-5-10-5z"/><path d="m2 12 10 5 10-5"/><path d="m2 17 10 5 10-5"/>"""
    "route" -> """<circle cx="6" cy="19" r="3"/><circle cx="18" cy="5" r="3"/><path d="M9 19h4a4 4 0 0 0 4-4V8"/>"""
    "lightbulb" -> """<path d="M9 18h6M10 22h4"/><path d="M12 2a6 6 0 0 0-4 10.5c.8.8 1.2 1.3 1.4 2.2l.1.3h5l.1-.3c.2-.9.6-1.4 1.4-2.2A6 6 0 0 0 12 2z"/>"""
    "star" -> """<path d="M12 3l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9 6.8 19.2l1-5.8L3.5 9.2l5.9-.9z"/>"""
    "chevron-down" -> """<path d="m6 9 6 6 6-6"/>"""
    "check" -> """<path d="M20 6 9 17l-5-5"/>"""
    "clock" -> """<circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>"""
    else -> ""
}
