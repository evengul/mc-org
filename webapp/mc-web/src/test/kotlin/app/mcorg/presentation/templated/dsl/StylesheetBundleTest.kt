package app.mcorg.presentation.templated.dsl

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The invariants that make one bundle safe (MCO-514). Each replaces a class of silent failure the
 * per-page link lists had — a sheet nobody linked, a page sheet quietly overriding a component —
 * with a test failure that names the file.
 */
class StylesheetBundleTest {

    /** `src/main/resources/static/styles`, via the classpath copy — tests read from a directory. */
    private val stylesDir: File by lazy {
        val url = javaClass.getResource(StylesheetBundle.dir)
            ?: error("${StylesheetBundle.dir} is not on the classpath")
        assertEquals("file", url.protocol, "expected the styles directory on disk, not in a jar")
        File(url.toURI())
    }

    /** `src/main/kotlin`, found from the compiled test class rather than the working directory. */
    private val sourceRoot: File by lazy {
        val classes = File(javaClass.protectionDomain.codeSource.location.toURI())
        generateSequence(classes) { it.parentFile }
            .map { File(it, "src/main/kotlin") }
            .first { it.isDirectory }
    }

    private fun sheetsOnDisk(): Set<String> =
        stylesDir.walkTopDown()
            .filter { it.isFile && it.extension == "css" }
            .map { it.relativeTo(stylesDir).path.removeSuffix(".css") }
            .toSet()

    @Test
    fun `the list is exactly the sheets on disk`() {
        val listed = StylesheetBundle.FILES.toSet() + StylesheetBundle.NOT_BUNDLED
        val onDisk = sheetsOnDisk()
        assertEquals(
            emptySet(), onDisk - listed,
            "stylesheets on disk that no page can ever load — add them to StylesheetBundle, or delete them",
        )
        assertEquals(
            emptySet(), listed - onDisk,
            "StylesheetBundle lists sheets that do not exist",
        )
        assertEquals(StylesheetBundle.FILES.size, StylesheetBundle.FILES.toSet().size, "a sheet is listed twice")
    }

    @Test
    fun `order is not a choice - base, then components and pages alphabetically`() {
        assertEquals(listOf("reset", "design-tokens"), StylesheetBundle.BASE)
        assertEquals(StylesheetBundle.COMPONENTS.sorted(), StylesheetBundle.COMPONENTS, "components are out of order")
        assertEquals(StylesheetBundle.PAGES.sorted(), StylesheetBundle.PAGES, "pages are out of order")
    }

    @Test
    fun `the bundle carries every sheet, in order, under a content hash`() {
        val bundle = StylesheetBundle.build()
        var at = -1
        for (file in StylesheetBundle.FILES) {
            val marker = bundle.content.indexOf("/* ==== $file.css ==== */")
            assertTrue(marker > at, "$file.css is missing from the bundle or out of order")
            at = marker
        }
        assertTrue(Regex("[0-9a-f]{12}").matches(bundle.version), "version should be a hash prefix: ${bundle.version}")
        assertEquals(bundle.version, StylesheetBundle.build().version, "the same content must hash the same")
        assertEquals("/static/seam.${bundle.version}.css", bundle.href)
    }

    @Test
    fun `versionOf reads a bundle URL and nothing else`() {
        assertEquals("abc123", StylesheetBundle.versionOf("/static/seam.abc123.css"))
        assertEquals("latest", StylesheetBundle.versionOf("/static/seam.latest.css"))
        assertNull(StylesheetBundle.versionOf("/static/styles/reset.css"))
        assertNull(StylesheetBundle.versionOf("/static/seam..css"))
        assertNull(StylesheetBundle.versionOf("/worlds/3"))
    }

    /**
     * With one bundle, a page sheet that re-declares a component's bare class no longer wins on
     * its own page — it wins everywhere. Five of those existed when the bundle landed (`.badge`
     * in the idea hub, three form helpers in settings); this is what keeps the count at zero.
     * Scoped overrides (`.worlds-head .page-heading`) are fine and are not matched.
     */
    @Test
    fun `no bare class is declared in two sheets`() {
        val bare = Regex("""^\.(-?[_a-zA-Z][\w-]*)(::?[\w-]+(\([^)]*\))?)*$""")
        val block = Regex("""([^{}]+)\{[^{}]*}""")
        val comment = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)

        val declaredIn = mutableMapOf<String, MutableSet<String>>()
        for (file in StylesheetBundle.FILES) {
            val css = File(stylesDir, "$file.css").readText().replace(comment, "")
            for (m in block.findAll(css)) {
                for (selector in m.groupValues[1].split(',')) {
                    val cls = bare.find(selector.trim())?.groupValues?.get(1) ?: continue
                    declaredIn.getOrPut(cls) { mutableSetOf() }.add(file)
                }
            }
        }
        val clashes = declaredIn.filterValues { it.size > 1 }
        assertTrue(
            clashes.isEmpty(),
            "A bare class declared in more than one sheet applies its last declaration to every page. " +
                "Scope the override to the page's own markup, or drop it:\n" +
                clashes.entries.joinToString("\n") { (cls, files) -> "  .$cls: ${files.sorted()}" },
        )
    }

    /** The regression path itself: a template linking a sheet by hand, beside the bundle. */
    @Test
    fun `no template links a stylesheet by hand`() {
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "StylesheetBundle.kt" }
            .filter { it.readText().contains("\"${StylesheetBundle.dir}/") }
            .map { it.relativeTo(sourceRoot).path }
            .toList()
        assertTrue(
            offenders.isEmpty(),
            "Every sheet is already in the bundle; a hand link is the per-page list coming back:\n" +
                offenders.joinToString("\n") { "  $it" },
        )
    }
}
