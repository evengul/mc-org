package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.user.TokenProfile
import kotlinx.html.*
import kotlinx.html.h1
import kotlinx.html.p
import kotlinx.html.stream.createHTML

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
            script {
                src = "https://cdn.jsdelivr.net/npm/htmx.org@2.0.8/dist/htmx.min.js"
                integrity = "sha384-/TgkGk7p307TH7EXJDuUlgG3Ce1UVolAOFopFekQkkXihi5u/6OCvVKyz1W+idaz"
                crossorigin = ScriptCrossorigin.anonymous
            }
            script {
                src = "https://cdn.jsdelivr.net/npm/htmx-ext-response-targets@2.0.4/dist/response-targets.js"
                integrity = "sha384-NtTh9TBZ2X/pFpfsVvQOjSsYWmjmqG6h5ioQWVAe2/j3AuTHRmfqvoqp+iOed+I0"
                crossorigin = ScriptCrossorigin.anonymous
            }
            script {
                src = ScriptBundle.href()
                defer = true
            }
        }
        body {
            attributes["hx-ext"] = "response-targets"
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
