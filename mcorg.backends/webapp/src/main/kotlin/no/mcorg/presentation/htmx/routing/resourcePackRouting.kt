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
            val userId = call.request.cookies["MCORG-USER-ID"]?.toIntOrNull() ?: return@get call.respondRedirect("/signin")

            call.handleResourcePacks(userId)
        }

        post("/resourcepacks") {
            call.handleCreateResourcePack()
        }

        delete("/resourcepacks/{resourcePackId}") {
            val resourcePackId = call.parameters["resourcePackId"]?.toIntOrNull()

            if (resourcePackId == null) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                packsApi().deletePack(resourcePackId)
                call.isHtml()
                call.respond("")
            }
        }

        get("/resourcepacks/{id}") {
            val id = call.parameters["id"]?.toInt() ?: return@get call.respondRedirect("/")
            call.handleResourcePack(id)
        }

        post("/resourcepacks/{id}") {
            call.handleAddResourceToPack()
        }

        delete("/resourcepacks/{id}/resources/{resourceId}") {
            val resourceId = call.parameters["resourceId"]?.toInt() ?: return@delete call.respondRedirect("/")
            packsApi().removeResource(resourceId)
            call.isHtml()
            call.respond("")
        }

        post("/worlds/{worldId}/resourcepacks") {
            call.handleSharePackWithWorld()
        }

        delete("/worlds/{worldId}/resourcepacks/{packId}") {
            call.handleUnSharePackWithWorld()
        }

        post("/worlds/{worldId}/teams/{teamId}/resourcepacks") {
            call.handleSharePackWithTeam()
        }

        delete("/worlds/{worldId}/teams/{teamId}/resourcepacks/{packId}") {
            call.handleUnSharePackWithTeam()
        }

        get("/htmx/resourcepacks/add") {
            call.isHtml()
            call.respond(addResourcePack())
        }

        get("/htmx/resourcepacks/{id}/resources/add") {
            val resourcePackId = call.parameters["id"]?.toIntOrNull() ?: return@get call.respondRedirect("/resourcepacks")
            call.isHtml()
            call.respond(addResourceToPack(resourcePackId))
        }
    }
}