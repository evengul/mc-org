package app.mcorg.presentation.handler

import app.mcorg.presentation.templated.legal.privacyPage
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

suspend fun ApplicationCall.handleGetPrivacy() {
    respondHtml(privacyPage())
}
