package app.mcorg.presentation.htmx.handlers

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import app.mcorg.domain.Authority
import app.mcorg.domain.PermissionLevel
import app.mcorg.presentation.configuration.packsApi
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.teamsApi
import app.mcorg.presentation.configuration.worldsApi
import app.mcorg.presentation.htmx.routing.getUserIdOrRedirect
import app.mcorg.presentation.htmx.routing.respondEmpty
import app.mcorg.presentation.htmx.routing.respondHtml
import app.mcorg.presentation.htmx.templates.pages.worldPage
import app.mcorg.presentation.htmx.templates.pages.worldsPage

suspend fun ApplicationCall.handleGetWorlds() {
    val userId = getUserIdOrRedirect() ?: return

    val permissions = permissionsApi()
        .getWorldPermissions(userId)
        .permissions[PermissionLevel.WORLD]!!

    val worlds = permissions.map { it.first }

    val isAdmin = permissions.filter { it.second == Authority.ADMIN }.map { it.first.id }

    respondHtml(worldsPage(worlds, isAdmin))
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

    val isWorldAdmin = permissionsApi()
        .hasWorldPermission(getUserIdOrRedirect()!!, Authority.ADMIN, id)

    respondHtml(worldPage(world, teams, packs, ownedPacks, isWorldAdmin))
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
            .addTeamPermission(userId, teamId, Authority.OWNER)

        respondRedirect("/")
    }
}

suspend fun ApplicationCall.handleDeleteWorld(id: Int) {
    worldsApi().deleteWorld(id)
    respondEmpty()
}