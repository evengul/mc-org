package no.mcorg.presentation.htmx.handlers

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.mcorg.domain.Authority
import no.mcorg.presentation.configuration.permissionsApi
import no.mcorg.presentation.configuration.teamsApi
import no.mcorg.presentation.configuration.worldsApi

suspend fun ApplicationCall.createWorld() {

    val userId = request.cookies["MCORG-USER-ID"]?.toIntOrNull()
        ?: return respondRedirect("/signin")

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