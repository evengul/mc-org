package no.mcorg.templates

import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun baseTemplate(siteTitle: String = "MC-ORG", body: BODY.() -> Unit): String {
    return "<!DOCTYPE html>\n" + createHTML().html {
        lang = "en"
        head {
            script {
                src = "/static/htmx.min.js"
            }
            link {
                href = "/static/main.css"
                rel = "stylesheet"
            }
            link {
                href = "/static/reset.css"
                rel = "stylesheet"
            }
            title {
                + siteTitle
            }
        }
        body {
            body()
        }
    }
}