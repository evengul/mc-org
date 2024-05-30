package no.mcorg.presentation.htmx.handlers

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.mcorg.domain.Authority
import no.mcorg.presentation.configuration.*
import no.mcorg.presentation.htmx.routing.isHtml
import no.mcorg.presentation.htmx.templates.pages.teamPage

suspend fun ApplicationCall.handleTeam(worldId: Int, teamId: Int) {
    val world = worldsApi().getWorld(worldId)
        ?: return respondRedirect("/")
    val team = teamsApi().getTeam(teamId)
        ?: return respondRedirect("/")

    val projects = projectsApi()
        .getTeamProjects(teamId)
    val packs = packsApi()
        .getTeamPacks(teamId)

    isHtml()
    respond(teamPage(world, team, projects, packs))
}

suspend fun ApplicationCall.handleCreateTeam() {
    val userId = request.cookies["MCORG-USER-ID"]?.toIntOrNull()

    if (userId == null) {
        respondRedirect("/signin")
    } else {
        val parts = receiveMultipart().readAllParts()
        val worldId = parameters["worldId"]?.toInt()
        val teamName = (parts.find { it.name == "team-name" } as PartData.FormItem?)?.value

        if (worldId == null || teamName == null || teamName.length < 3) {
            respond(HttpStatusCode.BadRequest)
        } else {
            val teamId = teamsApi().createTeam(worldId, teamName)
            permissionsApi().addTeamPermission(userId, teamId, Authority.OWNER)
            respondRedirect("/worlds/$worldId")
        }
    }

}