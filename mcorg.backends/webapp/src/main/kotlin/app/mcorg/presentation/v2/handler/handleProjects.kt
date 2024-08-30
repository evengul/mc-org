package app.mcorg.presentation.v2.handler

import app.mcorg.presentation.v2.configuration.projectsApi
import app.mcorg.presentation.v2.router.utils.clientRefresh
import app.mcorg.presentation.v2.router.utils.getUserId
import app.mcorg.presentation.v2.router.utils.respondHtml
import app.mcorg.presentation.v2.templates.project.projects
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