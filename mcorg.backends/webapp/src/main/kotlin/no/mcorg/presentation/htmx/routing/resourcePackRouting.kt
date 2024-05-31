package no.mcorg.presentation.htmx.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.mcorg.presentation.configuration.packsApi
import no.mcorg.presentation.htmx.handlers.*
import no.mcorg.presentation.htmx.templates.pages.addResourcePack
import no.mcorg.presentation.htmx.templates.pages.addResourceToPack

fun Application.resourcePackRouting() {
    routing {
        get("/resourcepacks") {
            call.respondResourcePacks()
        }

        post("/resourcepacks") {
            call.handleCreateResourcePack()
        }

        delete("/resourcepacks/{packId}") {
            val packId = call.getResourcePackParam(failOnMissingValue = true) ?: return@delete

            packsApi().deletePack(packId)
            call.respondEmpty()
        }

        get("/resourcepacks/{packid}") {
            val packId = call.getResourcePackParam() ?: return@get
            call.respondResourcePack(packId)
        }

        post("/resourcepacks/{packId}") {
            val packId = call.getResourcePackParam(failOnMissingValue = true) ?: return@post

            call.handleAddResourceToPack(packId)
        }

        delete("/resourcepacks/{packId}/resources/{resourceId}") {
            val (_, resourceId) = call.getResourcePackResourceParams(failOnMissingValue = true) ?: return@delete
            packsApi().removeResource(resourceId)
            call.respondEmpty()
        }

        post("/worlds/{worldId}/resourcepacks") {
            val worldId = call.getWorldParam(failOnMissingValue = true) ?: return@post
            call.handleSharePackWithWorld(worldId)
        }

        delete("/worlds/{worldId}/resourcepacks/{packId}") {
            val worldId = call.getWorldParam(failOnMissingValue = true) ?: return@delete
            val packId = call.getResourcePackParam(failOnMissingValue = true) ?: return@delete
            call.handleUnSharePackWithWorld(packId, worldId)
        }

        post("/worlds/{worldId}/teams/{teamId}/resourcepacks") {
            val (worldId, teamId) = call.getWorldTeamParams(failOnMissingValue = true) ?: return@post
            call.handleSharePackWithTeam(worldId, teamId)
        }

        delete("/worlds/{worldId}/teams/{teamId}/resourcepacks/{packId}") {
            val (_, teamId) = call.getWorldTeamParams(failOnMissingValue = true) ?: return@delete
            val packId = call.getResourcePackParam(failOnMissingValue = true) ?: return@delete
            call.handleUnSharePackWithTeam(teamId, packId)
        }

        get("/htmx/resourcepacks/add") {
            call.respondHtml(addResourcePack())
        }

        get("/htmx/resourcepacks/{packId}/resources/add") {
            val resourcePackId = call.getResourcePackParam() ?: return@get
            call.respondHtml(addResourceToPack(resourcePackId))
        }
    }
}