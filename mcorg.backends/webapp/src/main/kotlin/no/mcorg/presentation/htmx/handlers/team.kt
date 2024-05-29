package no.mcorg.presentation.htmx.handlers

import io.ktor.server.application.*
import io.ktor.server.response.*
import no.mcorg.presentation.configuration.packsApi
import no.mcorg.presentation.configuration.projectsApi
import no.mcorg.presentation.configuration.teamsApi
import no.mcorg.presentation.configuration.worldsApi
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