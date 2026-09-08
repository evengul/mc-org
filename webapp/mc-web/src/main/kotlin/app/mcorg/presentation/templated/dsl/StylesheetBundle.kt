package app.mcorg.presentation.templated.dsl

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
 * Delivery — the hashed URL, the LOCAL rebuild — is [AssetBundle]'s.
 */
object StylesheetBundle : AssetBundle(dir = "/static/styles", extension = "css") {

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

    override val files: List<String> =
        BASE + COMPONENTS.map { "components/$it" } + PAGES.map { "pages/$it" }

    /** Kept for the tests and the route; [files] is the abstract one. */
    val FILES: List<String> get() = files
}
