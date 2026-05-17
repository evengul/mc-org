package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.user.TokenProfile
import kotlinx.html.*
import kotlinx.html.h1
import kotlinx.html.p
import kotlinx.html.stream.createHTML

fun pageShell(
    pageTitle: String = "MC-ORG",
    user: TokenProfile? = null,
    stylesheets: List<String> = emptyList(),
    scripts: List<String> = emptyList(),
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
            title { +pageTitle }
            link {
                rel = "stylesheet"
                href = "/static/styles/reset.css"
            }
            link {
                rel = "stylesheet"
                href = "/static/styles/design-tokens.css"
            }
            link {
                rel = "stylesheet"
                href = "/static/styles/components/app-header.css"
            }
            link {
                rel = "stylesheet"
                href = "/static/styles/components/btn.css"
            }
            link {
                rel = "stylesheet"
                href = "/static/styles/components/modal.css"
            }
            link {
                rel = "stylesheet"
                href = "/static/styles/components/alert.css"
            }
            link {
                rel = "stylesheet"
                href = "/static/styles/components/badge.css"
            }
            link {
                rel = "stylesheet"
                href = "/static/styles/components/page-heading.css"
            }
            link {
                rel = "stylesheet"
                href = "/static/styles/components/section.css"
            }
            link {
                rel = "stylesheet"
                href = "/static/styles/components/tabs.css"
            }
            link {
                rel = "stylesheet"
                href = "/static/styles/components/data-table.css"
            }
            for (stylesheet in stylesheets) {
                link {
                    rel = "stylesheet"
                    href = stylesheet
                }
            }
            script {
                src = "https://cdn.jsdelivr.net/npm/htmx.org@2.0.8/dist/htmx.min.js"
                integrity = "sha384-/TgkGk7p307TH7EXJDuUlgG3Ce1UVolAOFopFekQkkXihi5u/6OCvVKyz1W+idaz"
                crossorigin = ScriptCrossorigin.anonymous
            }
            script {
                src = "https://cdn.jsdelivr.net/npm/htmx-ext-response-targets@2.0.4/dist/response-targets.js"
                integrity = "sha384-T41oglUPvXLGBVyRdZsVRxNWnOOqCynaPubjUVjxhsjFTKrFJGEMm3/0KGmNQ+Pg"
                crossorigin = ScriptCrossorigin.anonymous
            }
            script {
                src = "/static/scripts/confirmation-modal.js"
                defer = true
            }
            for (scriptSrc in scripts) {
                script {
                    src = scriptSrc
                    defer = true
                }
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
