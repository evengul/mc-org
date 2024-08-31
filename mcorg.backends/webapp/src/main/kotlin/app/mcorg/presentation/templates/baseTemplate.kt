package app.mcorg.presentation.templates

import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun baseTemplate(siteTitle: String = "MC-ORG", body: BODY.() -> Unit): String {
    return "<!DOCTYPE html>\n" + createHTML().html {
        lang = "en"
        head {
            title = siteTitle
            script {
                src = "/static/htmx.js"
            }
            link {
                href = "/static/root.css"
                rel = "stylesheet"
            }
            link {
                href = "/static/main.css"
                rel = "stylesheet"
            }
            link {
                href = "/static/reset.css"
                rel = "stylesheet"
            }
        }
        body {
            body()
        }
    }
}