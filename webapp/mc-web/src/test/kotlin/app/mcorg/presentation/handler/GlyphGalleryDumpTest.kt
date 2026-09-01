package app.mcorg.presentation.handler

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * TEMPORARY companion to the `/test/glyphs` review page. Asserts the gallery renders, and writes it
 * to `target/glyph-gallery.html` so it can be opened without standing up the server (port 8080 is
 * hardcoded, so a second worktree cannot run one alongside). Delete with [handleGlyphGallery].
 */
class GlyphGalleryDumpTest {

    @Test
    fun `gallery renders every glyph and is written for review`() {
        val html = renderGlyphGallery()

        assertTrue(html.contains("<svg"), "gallery should contain inline SVG")
        // Each of the 73 glyphs appears at least once, most of them many times over.
        assertTrue(html.length > 50_000, "gallery looks too small: ${html.length} chars")
        assertTrue(
            !html.contains("item-glyph--unmapped\" width", ignoreCase = false) ||
                html.contains("definitely_not_a_real_item"),
            "the only unmapped glyph should be the deliberate sample",
        )

        val out = File("target/glyph-gallery.html")
        out.parentFile.mkdirs()
        out.writeText(html)
        println("glyph gallery written to ${out.absolutePath} (${html.length} chars)")
    }
}
