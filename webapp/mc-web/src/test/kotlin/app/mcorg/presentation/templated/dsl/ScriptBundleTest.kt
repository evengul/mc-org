package app.mcorg.presentation.templated.dsl

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The invariants that make one script bundle safe (MCO-547), each replacing a silent failure the
 * per-page `scripts = listOf(...)` had: a file nobody linked, a global defined by two files with
 * the later one winning, a page rendering markup whose behaviour lived in a script it did not load.
 */
class ScriptBundleTest {

    private val scriptsDir: File by lazy {
        val url = javaClass.getResource(ScriptBundle.dir) ?: error("${ScriptBundle.dir} is not on the classpath")
        assertEquals("file", url.protocol, "expected the scripts directory on disk, not in a jar")
        File(url.toURI())
    }

    private val sourceRoot: File by lazy {
        val classes = File(javaClass.protectionDomain.codeSource.location.toURI())
        generateSequence(classes) { it.parentFile }
            .map { File(it, "src/main/kotlin") }
            .first { it.isDirectory }
    }

    @Test
    fun `the list is exactly the scripts on disk`() {
        val onDisk = scriptsDir.listFiles()!!.filter { it.extension == "js" }.map { it.nameWithoutExtension }.toSet()
        val listed = ScriptBundle.files.toSet()
        assertEquals(emptySet(), onDisk - listed, "scripts on disk that no page can ever run — add them to ScriptBundle, or delete them")
        assertEquals(emptySet(), listed - onDisk, "ScriptBundle lists scripts that do not exist")
        assertEquals(ScriptBundle.files.sorted(), ScriptBundle.files, "scripts are out of order; the order is alphabetical because it does not matter")
    }

    @Test
    fun `the bundle carries every script, each in its own IIFE, under a content hash`() {
        val bundle = ScriptBundle.build()
        var at = -1
        for (file in ScriptBundle.files) {
            val marker = bundle.content.indexOf("/* ==== $file.js ==== */\n(function () {\n")
            assertTrue(marker > at, "$file.js is missing from the bundle, unwrapped, or out of order")
            at = marker
        }
        assertTrue(Regex("[0-9a-f]{12}").matches(bundle.version), bundle.version)
        assertEquals("/static/seam.${bundle.version}.js", bundle.href)
        assertEquals("abc123", ScriptBundle.versionOf("/static/seam.abc123.js"))
        assertNull(ScriptBundle.versionOf("/static/seam.abc123.css"), "a stylesheet URL is not this bundle's")
        assertNull(StylesheetBundle.versionOf("/static/seam.abc123.js"), "and the other way round")
    }

    /**
     * A file exposes behaviour by assigning to `window`; wrapped in its own IIFE, nothing else
     * escapes. So two files assigning the same name is the only way one can clobber another —
     * which is exactly how `selectSearchedItem` used to be defined three times.
     */
    @Test
    fun `no window global is defined in two scripts`() {
        val assignment = Regex("""window\.([A-Za-z_]\w*)\s*=\s*(?:function|\()""")
        val definedIn = mutableMapOf<String, MutableSet<String>>()
        for (file in ScriptBundle.files) {
            for (m in assignment.findAll(File(scriptsDir, "$file.js").readText())) {
                definedIn.getOrPut(m.groupValues[1]) { mutableSetOf() }.add(file)
            }
        }
        val clashes = definedIn.filterValues { it.size > 1 }
        assertTrue(clashes.isEmpty(), "A global defined by two scripts keeps only the later one:\n" +
            clashes.entries.joinToString("\n") { (name, files) -> "  window.$name: ${files.sorted()}" })
        assertEquals(setOf("item-search"), definedIn["selectSearchedItem"]?.toSet(), "selectSearchedItem is item-search.js's alone")
    }

    /** The regression path itself: a template linking a script by hand, beside the bundle. */
    @Test
    fun `no template links a script by hand`() {
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "ScriptBundle.kt" }
            .filter { it.readText().contains("\"${ScriptBundle.dir}/") }
            .map { it.relativeTo(sourceRoot).path }
            .toList()
        assertTrue(offenders.isEmpty(), "Every script is already in the bundle:\n" + offenders.joinToString("\n") { "  $it" })
    }

    /**
     * Every item-search combo renders the hooks the one `selectSearchedItem` resolves from, so a
     * result click writes somewhere. A combo is a template whose input `hx-get`s `/items/search`;
     * the two panels in resource-panel.js intercept their own clicks in the capture phase and are
     * exempt.
     */
    @Test
    fun `every item-search combo renders the class hooks selectSearchedItem resolves`() {
        val wiresSearch = listOf("hx-get\"] = \"/items/search\"", "hxGet(\"/items/search\")")
        val combos = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(sourceRoot).path to it.readText() }
            .filter { (_, text) -> wiresSearch.any { text.contains(it) } }
            .toList()
        assertEquals(5, combos.size, "expected the draft form, the farm modal, the add-resource form and the two panels: ${combos.map { it.first }}")
        val missing = combos.filter { (path, text) ->
            val intercepted = path.endsWith("ProductionPanel.kt") || path.endsWith("ResourceDetailPanel.kt")
            !intercepted && !(text.contains("item-search-combo") && text.contains("item-search-input") && text.contains("item-search-selected-id"))
        }.map { it.first }
        assertTrue(missing.isEmpty(), "These render an item search without the hooks item-search.js needs:\n" + missing.joinToString("\n") { "  $it" })
    }
}
