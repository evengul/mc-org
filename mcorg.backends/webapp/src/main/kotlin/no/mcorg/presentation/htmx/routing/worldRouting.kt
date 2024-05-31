package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.mcorg.presentation.htmx.handlers.*
import no.mcorg.presentation.htmx.templates.pages.addWorld

fun Application.worldRouting() {
    routing {
        post("/worlds") {
            call.handleCreateWorld()
        }

        get("/worlds") {
            call.handleGetWorlds()
        }

        delete("/worlds/{worldId}") {
            val worldId = call.getWorldParam(failOnMissingValue = true) ?: return@delete

            call.handleDeleteWorld(worldId)
        }

        get("/worlds/{worldId}") {
            val worldId = call.getWorldParam() ?: return@get

            call.respondWorld(worldId)
        }

        get("/htmx/worlds/add") {
            call.respondHtml(addWorld())
        }
    }
}