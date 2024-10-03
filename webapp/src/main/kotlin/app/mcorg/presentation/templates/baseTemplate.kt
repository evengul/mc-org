package app.mcorg.presentation.templates

import io.ktor.util.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun baseTemplate(siteTitle: String = "MC-ORG", body: BODY.() -> Unit): String {
    return "<!DOCTYPE html>\n" + createHTML().html {
        lang = "en"
        head {
            title {
                + siteTitle
            }
            script {
                src = "/static/scripts/htmx.js"
                nonce = generateNonce()
            }
            script {
                src = "/static/scripts/dialogs.js"
                nonce = generateNonce()
            }
            link {
                href = "/static/styles/root.css"
                rel = "stylesheet"
            }
            link {
                href = "/static/styles/main.css"
                rel = "stylesheet"
            }
            link {
                href = "/static/styles/reset.css"
                rel = "stylesheet"
            }
            meta {
                content = "width=device-width, initial-scale=1"
                name = "viewport"
            }
        }
        body {
            body()
        }
    }
}