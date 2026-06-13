package app.mcorg.presentation.handler

import app.mcorg.presentation.templated.dsl.pageShell
import app.mcorg.presentation.utils.getHost
import app.mcorg.presentation.utils.removeToken
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.html.a
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h1


suspend fun ApplicationCall.handleGetSignOut() {
    val error = request.queryParameters["error"]
    val microsoftError = request.queryParameters["microsoft_error"]

    val errorCode = microsoftError ?: error

    if (errorCode == null) {
        response.cookies.removeToken(getHost() ?: "false")
        respondRedirect("/auth/sign-in", permanent = false)
        return
    }

    val errorArguments = request.queryParameters
        .filter(false) { key, _ -> key != "error" && key != "microsoft_error" }
        .toMap()
        .map { it.key to it.value.first() }

    respondHtml(pageShell(pageTitle = "Seam — Error") {
        h1 {
            + "An error occurred"
        }
        div("error-message") {
            + "Error: $errorCode"
        }
        errorArguments.forEach { (key, value) ->
            div("error-detail") {
                + "$key: $value"
            }
        }
        a {
            href = "/auth/sign-out"
            classes = setOf("btn", "btn--neutral")
            + "Sign Out"
        }
    })
}