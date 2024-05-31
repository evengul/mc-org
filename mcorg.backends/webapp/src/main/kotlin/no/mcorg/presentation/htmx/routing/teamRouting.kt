package no.mcorg.presentation.htmx.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.mcorg.presentation.configuration.teamsApi
import no.mcorg.presentation.htmx.handlers.handleCreateTeam
import no.mcorg.presentation.htmx.handlers.handleTeam
import no.mcorg.presentation.htmx.templates.pages.addTeam

fun Application.teamRouting() {
    routing {
        get("/worlds/{worldId}/teams/{teamId}") {
            val worldId = call.parameters["worldId"]?.toInt() ?: return@get call.respondRedirect("/worlds")
            val teamId = call.parameters["teamId"]?.toInt() ?: return@get call.respondRedirect("/worlds/$worldId")

            call.handleTeam(worldId, teamId)
        }

        post("/worlds/{worldId}/teams") {
            call.handleCreateTeam()
        }

        delete("/worlds/{worldId}/teams/{teamId}") {
            val worldId = call.parameters["worldId"]?.toInt()
            val teamId = call.parameters["teamId"]?.toInt()

            if (worldId == null || teamId == null) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                teamsApi().deleteTeam(teamId)
                call.isHtml()
                call.respond("")
            }
        }

        get("/htmx/world/{worldId}/add-team") {
            val worldId = call.parameters["worldId"]?.toInt()
            if (worldId == null) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                call.isHtml()
                call.respond(addTeam(worldId))
            }
        }
    }
}