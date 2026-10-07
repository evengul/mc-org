package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.user.TokenProfile
import kotlinx.html.*
import kotlinx.html.h1
import kotlinx.html.p
import kotlinx.html.stream.createHTML

internal const val HTMX_CONFIG =
    """{"implicitInheritance": true, "noSwap": [204, 304, "4xx", "5xx"], "includeIndicatorCSS": false}"""

fun pageShell(
    pageTitle: String = "Seam",
    user: TokenProfile? = null,
    body: BODY.() -> Unit
): String {
    return "<!DOCTYPE html>\n" + createHTML().html {
        lang = "en"
        head {
            meta { charset = "utf-8" }
            meta {
                name = "viewport"
                content = "width=device-width, initial-scale=1"
            }
            // The app is behind auth and not a marketing surface — keep it out
            // of search indexes so seam.gg stays the canonical result.
            meta {
                name = "robots"
                content = "noindex"
            }
            title { +pageTitle }
            meta {
                name = "theme-color"
                content = "#EBE1C8"
            }
            link {
                rel = "icon"
                type = "image/svg+xml"
                href = "/static/seam-favicon.svg"
            }
            link {
                rel = "icon"
                type = "image/png"
                href = "/static/favicon-32.png"
                attributes["sizes"] = "32x32"
            }
            link {
                rel = "apple-touch-icon"
                href = "/static/seam-icon-180.png"
                attributes["sizes"] = "180x180"
            }
            // One bundle at one content-hashed URL. There is deliberately no per-page list any
            // more — see StylesheetBundle for what that list cost.
            link {
                rel = "stylesheet"
                href = StylesheetBundle.href()
            }
            // Read once, when htmx loads, so it has to come before the script.
            //
            // implicitInheritance: htmx 4 made attribute inheritance opt-in (`hx-target:inherited`).
            // Containers here still set hx-target/hx-swap for the elements inside them.
            //
            // noSwap: htmx 4 swaps a 4xx/5xx into the target like any other response. Listing them
            // keeps htmx 2's "an error swaps nothing", so an error body cannot land in a list row.
            // Out-of-band swaps still run, which is how refusal alerts and field messages arrive
            // (ErrorHandler.kt), and an element's own `hx-status:<code>` still overrides it.
            //
            // includeIndicatorCSS: htmx's injected indicator rule also sets visibility:hidden,
            // which modal.css's own `.htmx-indicator` utility does not undo.
            meta {
                name = "htmx-config"
                content = HTMX_CONFIG
            }
            script {
                src = "https://cdn.jsdelivr.net/npm/htmx.org@4.0.0/dist/htmx.min.js"
                integrity = "sha384-BvJpBiO8Kh31EqtJe5DRIeWrHWnCGkwytKs9NKFi86Hhw96dEqdEMzZDeK9iEGTc"
                crossorigin = ScriptCrossorigin.anonymous
            }
            script {
                src = ScriptBundle.href()
                defer = true
            }
        }
        body {
            confirmDeleteModal()
            alertContainer()
            body()
        }
    }
}

fun FlowContent.container(block: FlowContent.() -> Unit) {
    div("container") { block() }
}

fun FlowContent.surface(block: FlowContent.() -> Unit) {
    div("surface") { block() }
}

fun FlowContent.divider() {
    div("divider") {}
}

fun FlowContent.pageHeading(title: String, subtitle: String? = null) {
    div("page-heading") {
        h1("page-heading__title") { +title }
        subtitle?.let { p("page-heading__subtitle") { +it } }
    }
}
