package no.mcorg.presentation.htmx.handlers

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.mcorg.domain.Authority
import no.mcorg.domain.PermissionLevel
import no.mcorg.presentation.configuration.*
import no.mcorg.presentation.htmx.routing.getUserIdOrRedirect
import no.mcorg.presentation.htmx.routing.respondHtml
import no.mcorg.presentation.htmx.templates.pages.teamPage

suspend fun ApplicationCall.respondTeam(worldId: Int, teamId: Int) {
    val userId = getUserIdOrRedirect() ?: return

    val world = worldsApi().getWorld(worldId)
        ?: return respondRedirect("/worlds")
    val team = teamsApi().getTeam(teamId)
        ?: return respondRedirect("/worlds/$worldId")

    val projects = projectsApi()
        .getTeamProjects(teamId)
    val packs = packsApi()
        .getTeamPacks(teamId)

    val ownedPacks = permissionsApi().getPackPermissions(userId)
        .permissions[PermissionLevel.PACK]!!
        .filter { it.second == Authority.OWNER }
        .map { it.first }

    respondHtml(teamPage(world, team, projects, packs, ownedPacks))
}

suspend fun ApplicationCall.handleCreateTeam(worldId: Int) {
    val userId = getUserIdOrRedirect() ?: return

    val parts = receiveMultipart().readAllParts()
    val teamName = (parts.find { it.name == "team-name" } as PartData.FormItem?)?.value

    if (teamName == null || teamName.length < 3) {
        respond(HttpStatusCode.BadRequest)
    } else {
        val teamId = teamsApi().createTeam(worldId, teamName)
        permissionsApi().addTeamPermission(userId, teamId, Authority.OWNER)
        respondRedirect("/worlds/$worldId")
    }

}