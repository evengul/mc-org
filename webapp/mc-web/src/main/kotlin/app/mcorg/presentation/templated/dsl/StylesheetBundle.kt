package app.mcorg.presentation.templated.dsl

import app.mcorg.config.AppConfig
import app.mcorg.domain.Local
import java.security.MessageDigest

/**
 * Every stylesheet the app has, served as one file at one content-addressed URL (MCO-514).
 *
 * Until this, `pageShell` linked a base set and each page listed the rest by hand, and that list
 * was the only thing joining a rendered class to its rules. Nothing checked it: move a component's
 * rules to another file and every page that rendered the class but did not list the new file went
 * unstyled, with the markup unchanged and every test green. It happened four times in a row
 * (the `/link` form, MCO-472, MCO-512, MCO-516), each fix adding a test for that one pairing —
 * and no per-page list can be right for markup that arrives from a shared htmx endpoint, which
 * does not know which page asked. One bundle makes the failure impossible rather than detectable.
 * It costs nothing worth having back: the whole set is ~44 KB gzipped, the heaviest page was
 * already fetching half of that across 26 requests, and after one page the bundle is cached.
 *
 * Order is fixed and not a choice: reset, tokens, components alphabetically, pages alphabetically.
 * A page sheet may *scope* a rule to its own markup (`.worlds-head .page-heading`) but must not
 * re-declare a component's bare class — with one bundle that override applies everywhere, not
 * just on the page that used to link it last. `StylesheetBundleTest` fails on both a file missing
 * from this list and a bare class declared in two files.
 *
 * The URL carries a hash of the content, so production caches it for a year and a deploy is
 * never paired with a stale sheet — which the per-file 24h max-age with no cache-busting allowed.
 * In LOCAL the bundle is rebuilt per call, so editing a sheet and refreshing keeps working.
 */
object StylesheetBundle {

    /** Classpath directory the sheets live in; `staticResources` still serves them one by one too. */
    const val DIR = "/static/styles"

    /** `/static/seam.<version>.css`. The route and the caching rule both key off this prefix. */
    const val HREF_PREFIX = "/static/seam."

    val BASE = listOf("reset", "design-tokens")

    val COMPONENTS = listOf(
        "alert", "app-header", "avatar", "badge", "btn", "callout", "danger-zone", "data-table",
        "drill", "empty-state", "error-page", "farm-panel", "form", "item-glyph", "item-search",
        "modal", "np-menu", "page-heading", "pending-invitations", "person-row", "progress",
        "project-card", "resource-panel", "resource-row", "resource-search", "section", "tabs",
        "task-list", "work-row", "world-tabs", "world-tally",
    )

    val PAGES = listOf(
        "admin-page", "draft-list", "idea-hub", "idea-wizard", "import-review", "landing-page",
        "profile-page", "project-detail", "project-list", "roadmap", "roadmap-graph",
        "settings-page", "worlds",
    )

    /**
     * On disk but not in the bundle: `pages/styleguide.css` belongs to the static `styleguide.html`,
     * which links the bundle and then that one sheet itself.
     */
    val NOT_BUNDLED = setOf("pages/styleguide")

    /** Paths relative to [DIR], without `.css`, in cascade order. */
    val FILES: List<String> =
        BASE + COMPONENTS.map { "components/$it" } + PAGES.map { "pages/$it" }

    class Bundle(val css: String, val version: String) {
        val href: String get() = "$HREF_PREFIX$version.css"
    }

    private val built: Bundle by lazy { build() }

    /** The bundle to serve now. Memoised outside LOCAL, where the sheets cannot change under a running JVM. */
    fun current(): Bundle = if (AppConfig.env is Local) build() else built

    fun href(): String = current().href

    /** The version in a bundle URL, or null when [path] is not one. `/static/seam.latest.css` → `latest`. */
    fun versionOf(path: String): String? {
        if (!path.startsWith(HREF_PREFIX) || !path.endsWith(".css")) return null
        return path.removePrefix(HREF_PREFIX).removeSuffix(".css").takeIf { it.isNotEmpty() }
    }

    fun build(): Bundle {
        val css = buildString {
            for (file in FILES) {
                append("/* ==== ").append(file).append(".css ==== */\n")
                append(read(file))
                append('\n')
            }
        }
        return Bundle(css, sha256Prefix(css))
    }

    private fun read(file: String): String {
        val path = "$DIR/$file.css"
        return javaClass.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("$path is listed in StylesheetBundle but is not on the classpath")
    }

    private fun sha256Prefix(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(12)
}
