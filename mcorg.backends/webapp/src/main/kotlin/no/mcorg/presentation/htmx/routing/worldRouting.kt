package no.mcorg.presentation.htmx.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.mcorg.domain.PermissionLevel
import no.mcorg.presentation.configuration.permissionsApi
import no.mcorg.presentation.htmx.handlers.createWorld
import no.mcorg.presentation.htmx.handlers.handleDeleteWorld
import no.mcorg.presentation.htmx.handlers.handleWorld
import no.mcorg.presentation.htmx.templates.pages.addWorld
import no.mcorg.presentation.htmx.templates.pages.worldsPage

fun Application.worldRouting() {
    routing {
        post("/worlds") {
            call.createWorld()
        }

        get("/worlds") {
            val userId = call.request.cookies["MCORG-USER-ID"]?.toIntOrNull()
                ?: return@get call.respondRedirect("/signin")
            val permissions = permissionsApi()
                .getWorldPermissions(userId)
                .permissions[PermissionLevel.WORLD]!!
                .map { it.first }
            call.isHtml()
            call.respond(worldsPage(permissions))
        }

        delete("/worlds/{worldId}") {
            val worldId = call.parameters["worldId"]?.toInt()
            if (worldId == null) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                call.handleDeleteWorld(worldId)
            }
        }

        get("/worlds/{id}") {
            val worldId = call.parameters["id"]?.toInt() ?: return@get call.respondRedirect("/")
            call.handleWorld(worldId)
        }

        get("/htmx/world/add") {
            call.isHtml()
            call.respond(addWorld())
        }
    }
}