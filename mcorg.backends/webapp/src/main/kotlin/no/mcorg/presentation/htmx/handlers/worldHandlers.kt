package no.mcorg.presentation.htmx.handlers

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.mcorg.domain.Authority
import no.mcorg.domain.PermissionLevel
import no.mcorg.presentation.configuration.packsApi
import no.mcorg.presentation.configuration.permissionsApi
import no.mcorg.presentation.configuration.teamsApi
import no.mcorg.presentation.configuration.worldsApi
import no.mcorg.presentation.htmx.routing.getUserIdOrRedirect
import no.mcorg.presentation.htmx.routing.respondEmpty
import no.mcorg.presentation.htmx.routing.respondHtml
import no.mcorg.presentation.htmx.templates.pages.worldPage
import no.mcorg.presentation.htmx.templates.pages.worldsPage

suspend fun ApplicationCall.handleGetWorlds() {
    val userId = getUserIdOrRedirect() ?: return

    val permissions = permissionsApi()
        .getWorldPermissions(userId)
        .permissions[PermissionLevel.WORLD]!!
        .map { it.first }

    respondHtml(worldsPage(permissions))
}

suspend fun ApplicationCall.respondWorld(id: Int) {
    val userId = getUserIdOrRedirect() ?: return

    val world = worldsApi().getWorld(id)
        ?: return respondRedirect("/worlds")

    val teams = teamsApi().getWorldTeams(world.id)
    val packs = packsApi().getWorldPacks(world.id)

    val ownedPacks = permissionsApi().getPackPermissions(userId)
        .permissions[PermissionLevel.PACK]!!
        .filter { it.second == Authority.OWNER }
        .map { it.first }

    respondHtml(worldPage(world, teams, packs, ownedPacks))
}

suspend fun ApplicationCall.handleCreateWorld() {

    val userId = getUserIdOrRedirect() ?: return

    val data = receiveMultipart().readAllParts()

    val worldName = data.find { it.name == "world-name" } as PartData.FormItem?
    val isMultiplayer = data.find { it.name == "is-multiplayer" } as PartData.FormItem?
    val teamName = data.find { it.name == "team-name" } as PartData.FormItem?

    if (worldName == null || (isMultiplayer != null && teamName == null)) {
        respond(HttpStatusCode.BadRequest)
    } else {
        val id = worldsApi()
            .createWorld(worldName.value)
        val teamId = teamsApi()
            .createTeam(id, teamName?.value ?: "WorldTeam")

        permissionsApi()
            .addWorldPermission(userId, id, Authority.OWNER)
        permissionsApi()
            .addTeamPermission(userId, teamId, Authority.PARTICIPANT)

        respondRedirect("/")
    }
}

suspend fun ApplicationCall.handleDeleteWorld(id: Int) {
    worldsApi().deleteWorld(id)
    respondEmpty()
}