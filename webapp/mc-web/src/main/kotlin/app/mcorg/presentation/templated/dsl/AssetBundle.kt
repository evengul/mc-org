package app.mcorg.presentation.templated.dsl

import app.mcorg.config.AppConfig
import app.mcorg.domain.Local
import java.security.MessageDigest

/**
 * A set of static files served as one content-addressed file: `/static/seam.<hash>.<extension>`.
 *
 * [StylesheetBundle] (MCO-514) and [ScriptBundle] (MCO-547) are the two. Both exist for the same
 * reason: a per-page list of `<link>`s or `<script>`s is the only thing joining rendered markup to
 * the file that styles or drives it, nothing checks that list, and markup from a shared htmx
 * endpoint cannot know which page will host it. One file per kind makes that failure impossible.
 *
 * The hash in the URL is what lets production cache it for a year: a deploy that changes any file
 * changes the URL, so a browser never pairs new HTML with a stale bundle. In LOCAL the bundle is
 * rebuilt per call, so editing a file and refreshing keeps working.
 */
abstract class AssetBundle(
    /** Classpath directory the files live in. `staticResources` still serves them one by one too. */
    val dir: String,
    val extension: String,
) {
    /** Paths relative to [dir], without the extension, in the order they are concatenated. */
    abstract val files: List<String>

    /** How one file's content appears in the bundle. Scripts get an IIFE; stylesheets nothing. */
    protected open fun wrap(content: String): String = content

    class Bundle(val content: String, val version: String, val href: String)

    private val built: Bundle by lazy { build() }

    /** The bundle to serve now. Memoised outside LOCAL, where the files cannot change under a running JVM. */
    fun current(): Bundle = if (AppConfig.env is Local) build() else built

    fun href(): String = current().href

    /**
     * The version named by a bundle URL of this kind, or null when [path] is not one.
     * `/static/seam.latest.css` → `latest` for the stylesheet bundle, null for the script bundle.
     */
    fun versionOf(path: String): String? {
        val suffix = ".$extension"
        if (!path.startsWith(HREF_PREFIX) || !path.endsWith(suffix)) return null
        return path.removePrefix(HREF_PREFIX).removeSuffix(suffix).takeIf { it.isNotEmpty() }
    }

    fun build(): Bundle {
        val content = buildString {
            for (file in files) {
                append("/* ==== ").append(file).append('.').append(extension).append(" ==== */\n")
                append(wrap(read(file)))
                append('\n')
            }
        }
        val version = sha256Prefix(content)
        return Bundle(content, version, "$HREF_PREFIX$version.$extension")
    }

    private fun read(file: String): String {
        val path = "$dir/$file.$extension"
        return javaClass.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("$path is listed in ${javaClass.simpleName} but is not on the classpath")
    }

    private fun sha256Prefix(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(12)

    companion object {
        /** `/static/seam.<version>.<ext>`. The routes and the caching rules key off this prefix. */
        const val HREF_PREFIX = "/static/seam."
    }
}
