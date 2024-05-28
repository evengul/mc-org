package no.mcorg.presentation.htmx.handlers

import io.ktor.server.application.*
import io.ktor.server.response.*
import no.mcorg.domain.PermissionLevel
import no.mcorg.presentation.configuration.permissionsApi
import no.mcorg.presentation.configuration.projectsApi
import no.mcorg.presentation.htmx.templates.pages.*

suspend fun ApplicationCall.handleIndex() {
    val userId = request.cookies["MCORG-USER-ID"]?.toIntOrNull() ?:
        return respondRedirect("/signin")

    val worlds = permissionsApi()
        .getWorldPermissions(userId)
        .permissions[PermissionLevel.WORLD]!!
        .map { it.first }

    response.headers.append("Content-Type", "text/html")

    if (worlds.isEmpty()) {
        respondRedirect("/first-contact")
    }

    if (worlds.size > 1) {
        respond(worldsPage(worlds))
    }

    val teams = permissionsApi()
        .getTeamPermissions(userId)
        .permissions[PermissionLevel.TEAM]!!
        .map { it.first }

    if (teams.isEmpty()) {
        respond(noTeamsPage())
    }

    if (teams.size > 1) {
        respond(teamsPage(worlds[0], teams))
    }

    val projects = projectsApi()
        .getTeamProjects(teams[0].id)

    if (projects.isEmpty()) {
        respond(createProject(worlds[0], teams[0]))
    }

    if (projects.size > 1) {
        respond(projectsPage(worlds[0], teams[0], projects))
    }

    val project = projectsApi()
        .getProject(projects[0].id)
            ?: throw IllegalStateException("Project was deleted before sending it back")

    respond(projectPage(project))

}