package no.mcorg.presentation.htmx.handlers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.date.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date

fun ApplicationCall.signOut() {
    response.cookies.append("MCORG-USER-TOKEN", "", expires = GMTDate(-1), httpOnly = true)
}

fun ApplicationCall.signIn(token: String) {
    response.cookies.append("MCORG-USER-TOKEN", token, httpOnly = true)
}

suspend fun ApplicationCall.getResourcePackParam(failOnMissingValue: Boolean = false): Int? {
    val packId = parameters["packId"]?.toIntOrNull()
    if (packId == null) {
        if (failOnMissingValue) response.status(HttpStatusCode.BadRequest)
        else respondRedirect("/resourcepacks")
        return null
    }
    return packId
}

suspend fun ApplicationCall.getResourcePackResourceParams(failOnMissingValue: Boolean = false): Pair<Int, Int>? {
    val packId = getResourcePackParam(failOnMissingValue) ?: return null
    val resourceId = parameters["resourceId"]?.toIntOrNull()
    if (resourceId == null) {
        if (failOnMissingValue) response.status(HttpStatusCode.BadRequest)
        else respondRedirect("/resourcepacks")
        return null
    }
    return packId to resourceId
}

suspend fun ApplicationCall.getWorldParam(failOnMissingValue: Boolean = false): Int? {
    val worldId = parameters["worldId"]?.toIntOrNull()
    if (worldId == null) {
        if (failOnMissingValue) respond(HttpStatusCode.BadRequest)
        else respondRedirect("/worlds")
        return null
    }
    return worldId
}

suspend fun ApplicationCall.getWorldTeamParams(failOnMissingValue: Boolean = false): Pair<Int, Int>? {
    val worldId = getWorldParam(failOnMissingValue) ?: return null
    val teamId = parameters["teamId"]?.toIntOrNull()
    if (teamId == null) {
        if (failOnMissingValue) respond(HttpStatusCode.BadRequest)
        else respondRedirect("/worlds/$worldId")
        return null
    }
    return worldId to teamId
}

suspend fun ApplicationCall.getWorldTeamProjectParams(failOnMissingValue: Boolean = false): WorldTeamProjectParams? {
    val (worldId, teamId) = getWorldTeamParams(failOnMissingValue) ?: return null
    val projectId = parameters["projectId"]?.toIntOrNull()
    if (projectId == null) {
        if (failOnMissingValue) respond(HttpStatusCode.BadRequest)
        else respondRedirect("/worlds/$worldId/teams/$teamId")
        return null
    }
    return WorldTeamProjectParams(worldId, teamId, projectId)
}

suspend fun ApplicationCall.getWorldTeamProjectTaskParams(failOnMissingValue: Boolean = false): WorldTeamProjectTaskParams? {
    val (worldId, teamId, projectId) = getWorldTeamProjectParams(failOnMissingValue) ?: return null
    val taskId = parameters["taskId"]?.toIntOrNull()
    if (taskId == null) {
        if (failOnMissingValue) respond(HttpStatusCode.BadRequest)
        else respondRedirect("/worlds/$worldId/teams/$teamId/projects/$projectId")

        return null
    }
    return WorldTeamProjectTaskParams(worldId, teamId, projectId, taskId)
}

data class WorldTeamProjectParams(val worldId: Int, val teamId: Int, val projectId: Int)

data class WorldTeamProjectTaskParams(val worldId: Int, val teamId: Int, val projectId: Int, val taskId: Int)