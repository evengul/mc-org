package app.mcorg.presentation.handler

import app.mcorg.item.ItemGlyph
import app.mcorg.presentation.templated.dsl.itemGlyph
import app.mcorg.presentation.templated.dsl.itemGlyphByName
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.*
import kotlinx.html.stream.createHTML

/**
 * TEMPORARY review page at `/test/glyphs` — every glyph in every tint, for eyeballing the set
 * before it is wired into the real surfaces. Delete this file and its route once the set is signed
 * off; nothing in the product links to it.
 */
suspend fun ApplicationCall.handleGlyphGallery() {
    respondHtml(renderGlyphGallery())
}

/** Real ids, one per interesting resolution path, to prove the id → glyph → tint chain end to end. */
private val SAMPLE_IDS = listOf(
    "minecraft:oak_planks", "cyan_wool", "netherite_pickaxe", "oxidized_cut_copper",
    "light_blue_concrete", "dark_oak_boat", "iron_ingot", "diamond", "redstone",
    "copper_axe", "copper_bulb", "carrot", "golden_carrot", "deepslate_diamond_ore",
    "spruce_stairs", "cherry_leaves", "allium", "creeper_spawn_egg", "melon_slice",
    "sulfur", "polished_cinnabar", "definitely_not_a_real_item",
)

internal fun renderGlyphGallery(): String = createHTML().html {
    head {
        meta(charset = "utf-8")
        title { +"Item glyphs — review" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        link(rel = "stylesheet", href = "/static/styles/reset.css")
        link(rel = "stylesheet", href = "/static/styles/design-tokens.css")
        link(rel = "stylesheet", href = "/static/styles/components/item-glyph.css")
        link(rel = "stylesheet", href = "/static/styles/pages/glyph-gallery.css")
    }
    body("glyph-page") {
        header("glyph-masthead") {
            h1 { +"Item glyphs" }
            p("glyph-standfirst") {
                +"${ItemGlyph.ALL.size} glyphs, drawn once per shape and tinted by material. "
                +"Temporary review page — not linked from the app."
            }
        }

        section("glyph-section") {
            h2 { +"Resolved from real item ids" }
            p("glyph-note") {
                +"The full chain: item id → rule → glyph → tint class. The last one has no rule, so "
                +"it renders as a visible gap rather than a plausible icon."
            }
            div("glyph-samples") {
                SAMPLE_IDS.forEach { id ->
                    div("glyph-sample") {
                        itemGlyph(id, size = 32)
                        div("glyph-sample__text") {
                            span("glyph-sample__id") { +ItemGlyph.bare(id) }
                            span("glyph-sample__meta") {
                                val g = ItemGlyph.resolve(id)
                                +(g?.name ?: "no glyph")
                                ItemGlyph.tint(id)?.let { +" · $it" }
                            }
                        }
                    }
                }
            }
        }

        section("glyph-section") {
            h2 { +"Inline at 16px" }
            p("glyph-note") { +"The size that actually matters — a materials row." }
            ul("glyph-inline-list") {
                listOf(
                    "oak_planks" to "64", "cobblestone" to "128", "iron_ingot" to "24",
                    "cyan_wool" to "16", "redstone" to "48", "diamond_pickaxe" to "1",
                    "spruce_stairs" to "32", "glowstone" to "12",
                ).forEach { (id, qty) ->
                    li {
                        itemGlyph(id, size = 16)
                        span("glyph-inline__name") { +ItemGlyph.bare(id).replace('_', ' ') }
                        span("glyph-inline__qty") { +qty }
                    }
                }
            }
        }

        // Untinted glyphs first, then one section per axis.
        val byAxis = ItemGlyph.ALL.groupBy { it.axis }

        section("glyph-section") {
            val plain = (byAxis[null].orEmpty() + byAxis[ItemGlyph.TintAxis.MATERIAL].orEmpty())
                .sortedBy { it.name }
            h2 { +"Untinted — ${plain.size} glyphs" }
            p("glyph-note") {
                +"Drawn once and never tinted. Includes the MATERIAL axis, where the shape spans too "
                +"many material families to colour meaningfully."
            }
            div("glyph-grid") {
                plain.forEach { g ->
                    figure("glyph-cell") {
                        itemGlyphByName(g.name, size = 48)
                        figcaption { +g.name }
                    }
                }
            }
        }

        listOf(
            ItemGlyph.TintAxis.COLOUR, ItemGlyph.TintAxis.WOOD,
            ItemGlyph.TintAxis.METAL, ItemGlyph.TintAxis.OXIDATION,
        ).forEach { axis ->
            val glyphs = byAxis[axis].orEmpty().sortedBy { it.name }
            val values = ItemGlyph.valuesFor(axis)
            section("glyph-section") {
                h2 { +"${axis.name.lowercase()} — ${glyphs.size} glyphs × ${values.size} tints" }
                div("glyph-matrix") {
                    div("glyph-matrix__head") {
                        span("glyph-matrix__corner") {}
                        values.forEach { v -> span("glyph-matrix__label") { +v.replace('_', ' ') } }
                    }
                    glyphs.forEach { g ->
                        div("glyph-matrix__row") {
                            span("glyph-matrix__name") { +g.name }
                            values.forEach { v ->
                                span("glyph-matrix__cell") { itemGlyphByName(g.name, v, size = 28) }
                            }
                        }
                    }
                }
            }
        }
    }
}
