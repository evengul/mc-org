package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.user.TokenProfile
import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun pageShell(
    pageTitle: String = "MC-ORG",
    user: TokenProfile? = null,
    stylesheets: List<String> = emptyList(),
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
                src = "/static/scripts/response-targets.js"
            }
        }
        body {
            attributes["hx-ext"] = "response-targets"
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
