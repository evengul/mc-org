package no.mcorg.presentation.htmx.handlers

import io.ktor.server.application.*
import io.ktor.server.response.*
import no.mcorg.presentation.configuration.packsApi
import no.mcorg.presentation.htmx.routing.isHtml
import no.mcorg.presentation.htmx.templates.pages.resourcePacksPage

suspend fun ApplicationCall.handleResourcePacks(userId: Int) {
    val packs = packsApi().getUserPacks(userId)

    isHtml()
    respond(resourcePacksPage(packs))
}
