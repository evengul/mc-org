package app.mcorg.pipeline.world

import app.mcorg.domain.model.project.Project
import app.mcorg.presentation.mockdata.MockProjects
import app.mcorg.presentation.mockdata.MockUsers
import app.mcorg.presentation.mockdata.MockWorlds
import app.mcorg.presentation.templated.world.kanbanView
import app.mcorg.presentation.templated.world.projectList
import app.mcorg.presentation.templated.world.roadmapView
import app.mcorg.presentation.templated.world.worldPage
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.presentation.utils.respondNotFound
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlinx.html.ul

suspend fun ApplicationCall.handleGetWorld() {
    val user = MockUsers.Evegul.tokenProfile()

    val tab = request.queryParameters["tab"]

    val world = parameters["worldId"]?.toIntOrNull()?.let { MockWorlds.getById(it) }
        ?: return respondNotFound("World ID is required")

    val projects = MockProjects.getProjectsByWorldId(world.id, includeCompleted = true)

    if (request.headers["HX-Request"] == "true" && tab != null) {
        handleGetTab(tab, projects)
        return
    }

    respondHtml(worldPage(user, world, projects, tab))
}

suspend fun ApplicationCall.handleGetTab(tab: String, projects: List<Project>) {
    when(tab) {
        "roadmap" -> respondHtml(createHTML().div {
            roadmapView()
        })
        "kanban" -> respondHtml(createHTML().div {
            kanbanView()
        })
        else -> respondHtml(createHTML().ul {
            projectList(projects)
        })
    }
}