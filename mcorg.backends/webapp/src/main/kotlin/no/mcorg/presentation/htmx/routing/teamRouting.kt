package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.mcorg.presentation.configuration.teamsApi
import no.mcorg.presentation.htmx.handlers.getWorldParam
import no.mcorg.presentation.htmx.handlers.getWorldTeamParams
import no.mcorg.presentation.htmx.handlers.handleCreateTeam
import no.mcorg.presentation.htmx.handlers.respondTeam
import no.mcorg.presentation.htmx.templates.pages.addTeam

fun Application.teamRouting() {
    routing {
        get("/worlds/{worldId}/teams/{teamId}") {
            val (worldId, teamId) = call.getWorldTeamParams() ?: return@get

            call.respondTeam(worldId, teamId)
        }

        post("/worlds/{worldId}/teams") {
            val worldId = call.getWorldParam(failOnMissingValue = true) ?: return@post
            call.handleCreateTeam(worldId)
        }

        delete("/worlds/{worldId}/teams/{teamId}") {
            val (_, teamId) = call.getWorldTeamParams() ?: return@delete

            teamsApi().deleteTeam(teamId)
            call.respondEmpty()
        }

        get("/htmx/worlds/{worldId}/teams/add") {
            val worldId = call.getWorldParam() ?: return@get

            call.respondHtml(addTeam(worldId))
        }
    }
}