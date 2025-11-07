package app.mcorg.presentation.handler

import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.page.createPage
import app.mcorg.presentation.utils.getHost
import app.mcorg.presentation.utils.removeToken
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
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

    respondHtml(createPage {
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
        neutralButton("Sign Out") {
            href = "/auth/sign-out"
        }
    })
}