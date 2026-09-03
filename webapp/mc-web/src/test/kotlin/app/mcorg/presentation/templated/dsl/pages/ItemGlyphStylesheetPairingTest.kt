package app.mcorg.presentation.templated.dsl.pages

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every page that loads `components/item-search.css` must also load `components/item-glyph.css`.
 *
 * The pairing is not local to any one template, which is what makes it easy to break. The glyph
 * markup arrives from `/items/search` — a shared endpoint that knows nothing about which page
 * asked — so a page can render a perfectly correct combo, get correct HTML back, and draw an
 * unstyled black box because it listed one of the two stylesheets. Nothing in the page's own
 * source would look wrong.
 *
 * Reading the templates rather than rendering them is deliberate: the alternative is fixture-heavy
 * ITs for five pages, and the invariant is a fact about the stylesheet lists, not about any
 * rendered output. `ItemGlyphSurfacesIT` covers the rendering end.
 */
class ItemGlyphStylesheetPairingTest {

    /**
     * The full quoted literal, not a substring. Prose mentioning `components/item-glyph.css` — and
     * the templates that draw glyphs do mention it — is not a stylesheet listing, and matching on
     * the bare filename made this test fail on its own explanatory comments.
     */
    private val searchCss = "\"/static/styles/components/item-search.css\""
    private val glyphCss = "\"/static/styles/components/item-glyph.css\""

    /** `src/main/kotlin`, found from the compiled test class rather than the working directory. */
    private val sourceRoot: File by lazy {
        val classes = File(javaClass.protectionDomain.codeSource.location.toURI())
        generateSequence(classes) { it.parentFile }
            .map { File(it, "src/main/kotlin") }
            .first { it.isDirectory }
    }

    private fun templates(): List<File> =
        sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `source root resolves`() {
        assertTrue(templates().size > 100, "found only ${templates().size} sources under $sourceRoot")
    }

    @Test
    fun `a page that loads item-search css also loads item-glyph css`() {
        val offenders = templates()
            .map { it to it.readText() }
            .filter { (_, text) -> text.contains(searchCss) && !text.contains(glyphCss) }
            .map { (file, _) -> file.relativeTo(sourceRoot).path }

        assertTrue(
            offenders.isEmpty(),
            "These pages host an item-search combo but never load $glyphCss, so /items/search " +
                "options render as unstyled markup on them. Add it next to $searchCss:\n" +
                offenders.joinToString("\n") { "  $it" },
        )
    }

    /**
     * The other direction, so the pairing does not decay into "glyph css everywhere". A file
     * listing the glyph stylesheet without the search one would be a page drawing glyphs from
     * some third surface — fine, but it should be a deliberate edit to this test, not a drift.
     */
    @Test
    fun `the glyph stylesheet is only listed beside the search one`() {
        val unexpected = templates()
            .map { it to it.readText() }
            .filter { (_, text) -> text.contains(glyphCss) && !text.contains(searchCss) }
            .map { (file, _) -> file.relativeTo(sourceRoot).path }

        assertTrue(
            unexpected.isEmpty(),
            "New glyph surface outside the search combo — intentional? Then widen this test:\n" +
                unexpected.joinToString("\n") { "  $it" },
        )
    }
}
