package no.mcorg.presentation.htmx.routing

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.button
import kotlinx.html.li
import kotlinx.html.stream.createHTML
import no.mcorg.domain.PermissionLevel
import no.mcorg.presentation.configuration.permissionsApi
import no.mcorg.presentation.configuration.projectsApi
import no.mcorg.presentation.configuration.teamsApi
import no.mcorg.presentation.htmx.handlers.*
import no.mcorg.presentation.htmx.templates.hxPut
import no.mcorg.presentation.htmx.templates.hxSwap
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

        get("/htmx/add-doable-task") {
            call.isHtml()
            call.respond(addDoableTask())
        }

        get("/htmx/add-countable-task") {
            call.isHtml()
            call.respond(addCountableTask())
        }

        post("/worlds/{worldId}/teams/{teamId}/projects/{projectId}") {
            call.handleCreateTask()
        }

        put("/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks/{taskId}/complete") {
            val worldId = call.parameters["worldId"]?.toInt() ?: return@put call.respondRedirect("/")
            val teamId = call.parameters["teamId"]?.toInt() ?: return@put call.respondRedirect("/")
            val projectId = call.parameters["projectId"]?.toInt() ?: return@put call.respondRedirect("/")
            val taskId = call.parameters["taskId"]?.toInt() ?: return@put call.respondRedirect("/")

            projectsApi().completeTask(taskId)

            call.respond(createHTML().button {
                hxPut("/worlds/$worldId/teams/$teamId/projects/$projectId/tasks/$taskId/incomplete")
                hxSwap("outerHTML")
                + "Undo completion"
            })
        }

        put("/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks/{taskId}/update-countable") {
            val worldId = call.parameters["worldId"]?.toInt() ?: return@put call.respondRedirect("/")
            val teamId = call.parameters["teamId"]?.toInt() ?: return@put call.respondRedirect("/")
            val projectId = call.parameters["projectId"]?.toInt() ?: return@put call.respondRedirect("/")
            val taskId = call.parameters["taskId"]?.toInt() ?: return@put call.respondRedirect("/")

            val parts = call.receiveMultipart().readAllParts()
            val needed = (parts.find { it.name == "needed" } as PartData.FormItem?)?.value?.toInt()
            val done = (parts.find { it.name == "done" } as PartData.FormItem?)?.value?.toInt()

            if (needed == null || done == null || needed < done) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                projectsApi().updateCountableTask(taskId, needed, done)

                val task = projectsApi().getTask(projectId, taskId)

                if (task == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(createHTML().li {
                        + task.name
                        updateCountableForm(worldId, teamId, projectId, task)
                        deleteTask(worldId, teamId, projectId, taskId)
                    })
                }
            }
        }

        put("/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks/{taskId}/incomplete") {
            val worldId = call.parameters["worldId"]?.toInt() ?: return@put call.respondRedirect("/")
            val teamId = call.parameters["teamId"]?.toInt() ?: return@put call.respondRedirect("/")
            val projectId = call.parameters["projectId"]?.toInt() ?: return@put call.respondRedirect("/")
            val taskId = call.parameters["taskId"]?.toInt() ?: return@put call.respondRedirect("/")

            projectsApi().undoCompleteTask(taskId)

            call.isHtml()
            call.respond(createHTML().button {
                hxPut("/worlds/$worldId/teams/$teamId/projects/$projectId/tasks/$taskId/complete")
                hxSwap("outerHTML")
                + "Complete"
            })
        }

        delete("/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks/{taskId}") {
            val taskId = call.parameters["taskId"]?.toInt() ?: return@delete call.respondRedirect("/")

            projectsApi().removeTask(taskId)

            call.isHtml()
            call.respond("")
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

