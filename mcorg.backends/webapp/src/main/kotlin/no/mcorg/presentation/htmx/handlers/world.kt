package no.mcorg.presentation.htmx.handlers

import io.ktor.server.application.*
import io.ktor.server.response.*
import no.mcorg.domain.Authority
import no.mcorg.domain.PermissionLevel
import no.mcorg.presentation.configuration.packsApi
import no.mcorg.presentation.configuration.permissionsApi
import no.mcorg.presentation.configuration.teamsApi
import no.mcorg.presentation.configuration.worldsApi
import no.mcorg.presentation.htmx.routing.isHtml
import no.mcorg.presentation.htmx.templates.pages.worldPage

suspend fun ApplicationCall.handleWorld(id: Int) {
    val userId = request.cookies["MCORG-USER-ID"]?.toIntOrNull()
        ?: return respondRedirect("/signin")

    val world = worldsApi().getWorld(id)
        ?: return respondRedirect("/")

    val teams = teamsApi().getWorldTeams(world.id)
    val packs = packsApi().getWorldPacks(world.id)

    val ownedPacks = permissionsApi().getPackPermissions(userId)
        .permissions[PermissionLevel.PACK]!!
        .filter { it.second == Authority.OWNER }
        .map { it.first }

    isHtml()

    respond(worldPage(world, teams, packs, ownedPacks))
}

suspend fun ApplicationCall.handleDeleteWorld(id: Int) {
    worldsApi().deleteWorld(id)
    respond("")
}