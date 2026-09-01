package app.mcorg.presentation.templated.dsl

import app.mcorg.item.ItemGlyph
import app.mcorg.item.ItemGlyphSprites
import kotlinx.html.FlowContent

/**
 * Shown when no rule covers an id. Deliberately reads as *missing* — a crossed box, in `--red` at
 * low opacity — rather than as a plausible generic item.
 *
 * A fallback that looks like a real icon is never reported: the page looks fine, so nobody says
 * anything, and the gap persists until someone happens to notice the wrong picture. Making the gap
 * visible turns every reader into a detector, which is the cheap half of the MCO-475 alerting.
 */
private const val UNMAPPED_BODY =
    """<path d="M8 8h32v32H8z"/><path d="m8 8 32 32M40 8 8 40"/>"""

/**
 * Inline item glyph for [itemId] — one of the 73 Seam-drawn Minecraft item icons.
 *
 * Emitted as a bare `<svg>` carrying the drawing's stroke attributes, because the tint works by
 * inheriting `currentColor` from a CSS class. An `<img src="…svg">` cannot do that: the referenced
 * document is isolated and never sees the page's colour. Same reasoning as [lucide].
 *
 * The tint class comes from the id's own prefix (`cyan_wool` → `.item-glyph--cyan`), so no caller
 * has to know which axis a glyph varies along. Colours live in
 * `static/styles/components/item-glyph.css`; nothing here sets an inline style.
 */
fun FlowContent.itemGlyph(
    itemId: String,
    size: Int = 16,
    vararg extraClasses: String,
) {
    val glyph = ItemGlyph.resolve(itemId)
    val body = glyph?.name?.let { ItemGlyphSprites.body(it) }
    val tint = ItemGlyph.tint(itemId)

    val classes = buildList {
        add("item-glyph")
        if (body == null) add("item-glyph--unmapped") else tint?.let { add("item-glyph--${it.replace('_', '-')}") }
        addAll(extraClasses)
    }.joinToString(" ")

    renderGlyphSvg(classes, size, body ?: UNMAPPED_BODY, ItemGlyph.bare(itemId))
}

/**
 * Renders a glyph by name and explicit tint, bypassing id resolution. For the glyph gallery, which
 * shows combinations no single item id produces.
 */
fun FlowContent.itemGlyphByName(
    name: String,
    tint: String? = null,
    size: Int = 16,
) {
    val body = ItemGlyphSprites.body(name)
    val classes = buildList {
        add("item-glyph")
        if (body == null) add("item-glyph--unmapped") else tint?.let { add("item-glyph--${it.replace('_', '-')}") }
    }.joinToString(" ")
    renderGlyphSvg(classes, size, body ?: UNMAPPED_BODY, name)
}

private fun FlowContent.renderGlyphSvg(classes: String, size: Int, body: String, label: String) {
    // Built before the unaryPlus: inside onTagContentUnsafe, `+"a" + "b"` parses as
    // unaryPlus("a").plus("b"), not as string concatenation.
    val svg = """<svg class="$classes" width="${size}px" height="${size}px" viewBox="0 0 48 48" """ +
        """fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" """ +
        """stroke-linejoin="round" role="img" aria-label="$label">$body</svg>"""
    consumer.onTagContentUnsafe { +svg }
}
