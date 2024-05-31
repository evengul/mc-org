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
import no.mcorg.presentation.configuration.projectsApi
import no.mcorg.presentation.htmx.handlers.handleCreateTask
import no.mcorg.presentation.htmx.templates.hxPut
import no.mcorg.presentation.htmx.templates.hxSwap
import no.mcorg.presentation.htmx.templates.pages.addCountableTask
import no.mcorg.presentation.htmx.templates.pages.addDoableTask
import no.mcorg.presentation.htmx.templates.pages.deleteTask
import no.mcorg.presentation.htmx.templates.pages.updateCountableForm

fun Application.taskRouting() {
    routing {
        post("/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks") {
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

        get("/htmx/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks/add-countable") {
            val worldId = call.parameters["worldId"]?.toInt() ?: return@get call.respondRedirect("/worlds")
            val teamId = call.parameters["teamId"]?.toInt() ?: return@get call.respondRedirect("/worlds/$worldId")
            val projectId = call.parameters["projectId"]?.toInt() ?: return@get call.respondRedirect("/worlds/$worldId/teams/$teamId")
            call.isHtml()
            call.respond(addCountableTask(worldId, teamId, projectId))
        }

        get("/htmx/worlds/{worldId}/teams/{teamId}/projects/{projectId}/tasks/add-doable") {
            val worldId = call.parameters["worldId"]?.toInt() ?: return@get call.respondRedirect("/worlds")
            val teamId = call.parameters["teamId"]?.toInt() ?: return@get call.respondRedirect("/worlds/$worldId")
            val projectId = call.parameters["projectId"]?.toInt() ?: return@get call.respondRedirect("/worlds/$worldId/teams/$teamId")
            call.isHtml()
            call.respond(addDoableTask(worldId, teamId, projectId))
        }
    }
}