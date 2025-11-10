package app.mcorg.pipeline.world

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.user.WorldMember
import app.mcorg.domain.model.world.World
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.notification.getUnreadNotificationsOrZero
import app.mcorg.pipeline.project.commonsteps.SearchProjectsInput
import app.mcorg.pipeline.project.commonsteps.SearchProjectsStep
import app.mcorg.pipeline.world.commonsteps.GetWorldMemberStep
import app.mcorg.pipeline.world.commonsteps.GetWorldStep
import app.mcorg.pipeline.world.roadmap.GetWorldRoadMapStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.world.WorldPageTabData
import app.mcorg.presentation.templated.world.worldPage
import app.mcorg.presentation.templated.world.worldProjectContent
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.div
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetWorld() {
    val user = this.getUser()
    val worldId = this.getWorldId()

    val tab = request.queryParameters["tab"]

    val notifications = getUnreadNotificationsOrZero(user.id)

    executePipeline(
        onSuccess = { tabData ->
            if (request.headers["HX-Request"] == "true" && tab != null) {
                respondHtml(createHTML().div("world-project-content") {
                    worldProjectContent(tabData)
                })
            } else {
                respondHtml(worldPage(user, tabData, notifications))
            }
        }
    ) {
        value(tab)
            .step(GetTabDataStep(worldId, user.id))
    }
}

private data class GetTabDataStep(val worldId: Int, val userId: Int) : Step<String?, AppFailure, WorldPageTabData> {
    override suspend fun process(input: String?): Result<AppFailure, WorldPageTabData> {
        return when (input) {
            "roadmap" -> {
                val roadmap = GetWorldRoadMapStep(worldId).process(Unit)
                if (roadmap is Result.Failure) return Result.failure(roadmap.error)

                getCommonTabData(worldId, userId)
                    .map { (world, member, projects) -> WorldPageTabData.RoadmapData(projects, world, member, roadmap.getOrNull()!!) }

            }
            "kanban" -> {
                getCommonTabData(worldId, userId)
                    .map { (world, member, projects) -> WorldPageTabData.KanbanData(projects, world, member) }
            }
            else -> {
                getCommonTabData(worldId, userId)
                    .map { (world, member, projects) -> WorldPageTabData.ProjectsData(projects, world, member) }
            }
        }
    }
}

private suspend fun getCommonTabData(worldId: Int, userId: Int): Result<AppFailure, Triple<World, WorldMember, List<Project>>> {
    val world = GetWorldStep.process(worldId)
    val worldMember = GetWorldMemberStep(worldId).process(userId)
    val projects = SearchProjectsStep.process(SearchProjectsInput(worldId))

    if (world is Result.Failure) return Result.failure(world.error)
    if (worldMember is Result.Failure) return Result.failure(worldMember.error)
    if (projects is Result.Failure) return Result.failure(projects.error)

    return Result.success(Triple(world.getOrNull()!!, worldMember.getOrNull()!!, projects.getOrNull()!!))
}