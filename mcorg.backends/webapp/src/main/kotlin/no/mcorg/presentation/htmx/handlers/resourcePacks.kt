package no.mcorg.presentation.htmx.handlers

import io.ktor.server.application.*
import io.ktor.server.response.*
import no.mcorg.presentation.configuration.packsApi
import no.mcorg.presentation.htmx.routing.isHtml
import no.mcorg.presentation.htmx.templates.pages.resourcePackPage

suspend fun ApplicationCall.handleResourcePack(packId: Int) {
    val pack = packsApi().getPack(packId) ?: return respondRedirect("/")

    isHtml()

    respond(resourcePackPage(pack))
}