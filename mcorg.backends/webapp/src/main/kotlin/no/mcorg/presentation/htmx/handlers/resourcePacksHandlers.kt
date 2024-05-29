package no.mcorg.presentation.htmx.handlers

import io.ktor.server.application.*
import io.ktor.server.response.*
import no.mcorg.domain.PermissionLevel
import no.mcorg.presentation.configuration.permissionsApi
import no.mcorg.presentation.htmx.routing.isHtml
import no.mcorg.presentation.htmx.templates.pages.resourcePacksPage

suspend fun ApplicationCall.handleResourcePacks(userId: Int) {
    val packs = permissionsApi().getPackPermissions(userId)
        .permissions[PermissionLevel.PACK]!!
        .map { it.first }

    isHtml()
    respond(resourcePacksPage(packs))
}
