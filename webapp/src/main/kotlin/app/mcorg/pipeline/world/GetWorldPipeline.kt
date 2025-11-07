package app.mcorg.pipeline.world

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.user.WorldMember
import app.mcorg.domain.model.world.World
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.notification.getUnreadNotificationsOrZero
import app.mcorg.pipeline.project.commonsteps.SearchProjectsInput
import app.mcorg.pipeline.project.commonsteps.SearchProjectsStep
import app.mcorg.pipeline.world.commonsteps.GetWorldMemberStep
import app.mcorg.pipeline.world.commonsteps.GetWorldStep
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.templated.world.kanbanView
import app.mcorg.presentation.templated.world.projectList
import app.mcorg.presentation.templated.world.roadmapView
import app.mcorg.presentation.templated.world.worldPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlinx.html.ul

data class GetWorldSuccess(
    val world: World,
    val worldMember: WorldMember,
    val projects: List<Project>,
)

suspend fun ApplicationCall.handleGetWorld() {
    val user = this.getUser()
    val worldId = this.getWorldId()

    val tab = request.queryParameters["tab"]

    val notifications = getUnreadNotificationsOrZero(user.id)

    executeParallelPipeline(
        onSuccess = { (world, worldMember, projects) ->
            if (request.headers["HX-Request"] == "true" && tab != null) {
                handleGetTab(tab, projects)
            } else {
                respondHtml(worldPage(user, world, worldMember, projects, tab, notifications))
            }
        }
    ) {
        val getWorld = singleStep("world", worldId, GetWorldStep)
        val getProjects = singleStep("projects", SearchProjectsInput(worldId = worldId), SearchProjectsStep)
        val getWorldMember = singleStep("worldMember", user.id, GetWorldMemberStep(worldId))


        merge("worldData", getWorld, getProjects, getWorldMember) { world, projects, member ->
            Result.success(GetWorldSuccess(world, member, projects))
        }
    }
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