package app.mcorg.item

import org.slf4j.LoggerFactory

/**
 * The 73 glyph drawings, read once from `static/icons/items/` on the classpath.
 *
 * The bodies are **inlined** into the page rather than referenced as `<img src>`, because an SVG
 * loaded through `<img>` is an isolated document and cannot inherit `currentColor` — and inheriting
 * the surrounding colour is the entire tint mechanism. This is the same reason
 * [app.mcorg.presentation.templated.dsl.lucide] inlines rather than using [Icons].
 *
 * The files are also served as static assets, so anything that wants a plain URL still has one.
 */
object ItemGlyphSprites {

    private val logger = LoggerFactory.getLogger(ItemGlyphSprites::class.java)

    private const val DIR = "/static/icons/items"

    /** Inner markup of each glyph, keyed by glyph name — the outer `<svg>` wrapper removed. */
    private val bodies: Map<String, String> by lazy {
        ItemGlyph.ALL.mapNotNull { glyph ->
            val body = load(glyph.name)
            if (body == null) {
                logger.error("Item glyph '{}' has no SVG at {}/{}.svg", glyph.name, DIR, glyph.name)
                null
            } else {
                glyph.name to body
            }
        }.toMap()
    }

    private fun load(name: String): String? {
        val raw = javaClass.getResourceAsStream("$DIR/$name.svg")
            ?.bufferedReader()?.use { it.readText() }
            ?: return null
        // Keep only what sits between the outer <svg …> and </svg>; the wrapper is re-emitted per
        // call site with its own sizing and classes.
        val open = raw.indexOf('>')
        val close = raw.lastIndexOf("</svg>")
        if (open < 0 || close <= open) {
            logger.error("Item glyph '{}' is not a well-formed SVG document", name)
            return null
        }
        return raw.substring(open + 1, close).trim()
    }

    /** Inner markup for [name], or null when no such glyph is bundled. */
    fun body(name: String): String? = bodies[name]

    /** Glyph names that actually loaded. */
    fun names(): Set<String> = bodies.keys

    /**
     * Filenames present in the sprite directory, for the test that catches an SVG no rule can ever
     * select. Returns null when the resources are inside a jar, where a directory cannot be listed
     * — tests and local runs read from a directory, which is where the check matters.
     */
    fun filesOnDisk(): List<String>? {
        val url = javaClass.getResource(DIR) ?: return null
        if (url.protocol != "file") return null
        return java.io.File(url.toURI()).listFiles()
            ?.filter { it.name.endsWith(".svg") }
            ?.map { it.name.removeSuffix(".svg") }
            ?.sorted()
    }
}
