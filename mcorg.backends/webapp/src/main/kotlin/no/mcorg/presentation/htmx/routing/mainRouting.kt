package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.mcorg.domain.PermissionLevel
import no.mcorg.presentation.configuration.permissionsApi
import no.mcorg.presentation.htmx.handlers.*
import no.mcorg.presentation.htmx.templates.pages.firstWorldTeam
import no.mcorg.presentation.htmx.templates.pages.worldsPage

fun Application.mainRouting() {
    routing {
        get("/") {
            call.handleIndex()
        }
        get("/first-contact") {
            call.handleFirstContact()
        }
        get("/first-world-team") {
            call.isHtml()

            if (call.request.queryParameters["is-multiplayer"] == "on") {
                call.respond(firstWorldTeam())
            } else {
                call.respond("")
            }
        }
        get("/signin") {
            call.handleGetSignin()
        }
        get("/register") {
            call.handleGetRegister()
        }
        post("/signin") {
            call.handlePostSignin()
        }
        post("/register") {
            call.handlePostRegister()
        }
        get("/signout") {
            call.handleGetSignout()
        }
        post("/first-contact") {
            call.createWorld()
        }

        get("/worlds") {
            val userId = call.request.cookies["MCORG-USER-ID"]?.toIntOrNull()
                ?: return@get call.respondRedirect("/signin")
            call.isHtml()
            call.respond(worldsPage(permissionsApi().getWorldPermissions(userId).permissions[PermissionLevel.WORLD]!!.map { it.first }))
        }

        get("/worlds/{id}") {
            val worldId = call.parameters["id"]?.toInt() ?: return@get call.respondRedirect("/")
            call.handleWorld(worldId)
        }

        get("/worlds/{worldId}/teams/{teamId}") {
            val worldId = call.parameters["worldId"]?.toInt() ?: return@get call.respondRedirect("/")
            val teamId = call.parameters["teamId"]?.toInt() ?: return@get call.respondRedirect("/")

            call.handleTeam(worldId, teamId)
        }

        get("/worlds/{worldId}/teams/{teamId}/projects/{projectId}") {
            val projectId = call.parameters["worldId"]?.toInt() ?: return@get call.respondRedirect("/")

            call.handleProject(projectId)
        }

        get("/resourcepacks") {
            val userId = call.request.cookies["MCORG-USER-ID"]?.toIntOrNull() ?: return@get call.respondRedirect("/signin")

            call.handleResourcePacks(userId)
        }

        get("/resourcepacks/{id}") {
            val id = call.parameters["id"]?.toInt() ?: return@get call.respondRedirect("/")
            call.handleResourcePack(id)
        }
    }
}

fun ApplicationCall.isHtml() {
    response.headers.append("Content-Type", "text/html")
}

