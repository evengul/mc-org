package no.mcorg.presentation.htmx.handlers

import io.ktor.server.application.*
import io.ktor.server.response.*
import no.mcorg.domain.Authority
import no.mcorg.presentation.configuration.permissionsApi
import no.mcorg.presentation.configuration.teamsApi
import no.mcorg.presentation.configuration.worldsApi

suspend fun ApplicationCall.createWorld() {
    val id = worldsApi()
        .createWorld("Testworld")
    val teamId = teamsApi()
        .createTeam(id, "TestTeam")

    permissionsApi()
        .addWorldPermission(1, id, Authority.OWNER)
    permissionsApi()
        .addTeamPermission(1, teamId, Authority.PARTICIPANT)

    respondRedirect("/")
}