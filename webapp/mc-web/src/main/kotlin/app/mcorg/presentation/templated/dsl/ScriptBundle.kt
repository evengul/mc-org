package app.mcorg.presentation.templated.dsl

/**
 * Every page script the app has, served as one deferred file at one content-addressed URL
 * (MCO-547 — the JS half of what MCO-514 did for CSS).
 *
 * The audit behind it found no page running without a script it needed, but found the two
 * neighbours of that: nine files linked by nothing and called from nowhere (deleted), and the one
 * shared-fragment coupling — `/items/search` rendering `onclick="selectSearchedItem(this)"` —
 * solved by defining that global three times, once per host page. It now lives once, in
 * `item-search.js`, and `ScriptBundleTest` fails if any `window.<name> =` is defined in two files.
 *
 * Every file runs on every page, so each must be safe where its markup is absent. The ones here
 * are: they delegate from the document, or look their element up and return when it is missing.
 * Each file is also wrapped in its own IIFE by [wrap], so top-level `const`s in one file cannot
 * collide with another's — a file exposes behaviour by assigning to `window`, nothing else.
 *
 * Order is alphabetical and does not matter: nothing here calls into another file at load time.
 * The two SRI-pinned htmx scripts stay separate `<script>` tags in `Layout.kt`; they are not ours.
 */
object ScriptBundle : AssetBundle(dir = "/static/scripts", extension = "js") {

    override val files: List<String> = listOf(
        "confirmation-modal",
        "farm-modal",
        "farm-suggestions",
        "import-review",
        "item-search",
        "np-menu",
        "plan-view",
        "resource-panel",
        "resource-search",
        "worlds",
    )

    override fun wrap(content: String): String = "(function () {\n$content\n})();"
}
