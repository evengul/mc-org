package app.mcorg.presentation.handler

import app.mcorg.presentation.configuration.projectsApi
import app.mcorg.presentation.router.utils.clientRefresh
import app.mcorg.presentation.router.utils.getUserId
import app.mcorg.presentation.router.utils.respondHtml
import app.mcorg.presentation.templates.project.projects
import io.ktor.server.application.*

suspend fun ApplicationCall.handleGetProjects() {
    respondHtml(projects(projectsApi.getWorldProjects(getUserId())))
}

suspend fun ApplicationCall.handleGetAddProject() {
    respondHtml("Add project")
}

suspend fun ApplicationCall.handlePostProject() {
    // TODO: Read form data and create the project
    clientRefresh()
}

suspend fun ApplicationCall.handleGetProject() {
    respondHtml("Project page")
}

suspend fun ApplicationCall.handleGetAssignProject() {
    respondHtml("Assign project")
}

suspend fun ApplicationCall.handlePostProjectAssignee() {
    // TODO: Read form data and add user if it exists
    clientRefresh()
}