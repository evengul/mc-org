package no.mcorg.presentation.htmx.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.mcorg.domain.PermissionLevel
import no.mcorg.presentation.configuration.permissionsApi
import no.mcorg.presentation.configuration.projectsApi
import no.mcorg.presentation.configuration.teamsApi
import no.mcorg.presentation.htmx.handlers.*
import no.mcorg.presentation.htmx.templates.pages.*

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
        post("/worlds") {
            call.createWorld()
        }
        delete("/worlds/{worldId}") {
            val worldId = call.parameters["worldId"]?.toInt()
            if (worldId == null) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                call.handleDeleteWorld(worldId)
            }
        }

        get("/worlds") {
            val userId = call.request.cookies["MCORG-USER-ID"]?.toIntOrNull()
                ?: return@get call.respondRedirect("/signin")
            call.isHtml()
            call.respond(worldsPage(permissionsApi().getWorldPermissions(userId).permissions[PermissionLevel.WORLD]!!.map { it.first }))
        }

        get("/htmx/create-world") {
            call.isHtml()
            call.respond(addWorld())
        }

        get("/worlds/{id}") {
            val worldId = call.parameters["id"]?.toInt() ?: return@get call.respondRedirect("/")
            call.handleWorld(worldId)
        }

        get("/htmx/create-team") {
            call.isHtml()
            call.respond(addTeam())
        }

        post("/worlds/{worldId}") {
            call.handleCreateTeam()
        }

        get("/htmx/create-project") {
            call.isHtml()
            call.respond(addProject())
        }

        get("/worlds/{worldId}/teams/{teamId}") {
            val worldId = call.parameters["worldId"]?.toInt() ?: return@get call.respondRedirect("/")
            val teamId = call.parameters["teamId"]?.toInt() ?: return@get call.respondRedirect("/")

            call.handleTeam(worldId, teamId)
        }

        post("/worlds/{worldId}/teams/{teamId}") {
            call.handleCreateProject()
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

        get("/worlds/{worldId}/teams/{teamId}/projects/{projectId}") {
            val projectId = call.parameters["projectId"]?.toInt() ?: return@get call.respondRedirect("/")

            call.handleProject(projectId)
        }

        delete("/worlds/{worldId}/teams/{teamId}/projects/{projectId}") {
            val worldId = call.parameters["worldId"]?.toInt()
            val teamId = call.parameters["teamId"]?.toInt()
            val projectId = call.parameters["projectId"]?.toInt()

            if (worldId == null || teamId == null || projectId == null) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                projectsApi().deleteProject(projectId)
                call.isHtml()
                call.respond("")
            }
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

