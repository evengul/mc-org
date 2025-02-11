package app.mcorg.presentation.templates

import app.mcorg.presentation.hxExtension
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
                src = "/static/scripts/response-targets.js"
            }
            script {
                src = "/static/scripts/dialogs.js"
                nonce = generateNonce()
            }
            script {
                src = "/static/scripts/draggable.js"
                nonce = generateNonce()
            }
            script {
                src = "/static/scripts/remove-first-project-dialog-on-create.js"
                nonce = generateNonce()
            }
            link {
                href = "/static/styles/reset.css"
                rel = "stylesheet"
            }
            link {
                href = "/static/styles/root.css"
                rel = "stylesheet"
            }
            link {
                href = "/static/styles/styles.css"
                rel = "stylesheet"
            }
            meta {
                content = "width=device-width, initial-scale=1"
                name = "viewport"
            }
        }
        body {
            hxExtension("response-targets")
            body()
        }
    }
}