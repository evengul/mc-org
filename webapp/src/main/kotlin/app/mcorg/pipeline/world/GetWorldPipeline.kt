package app.mcorg.pipeline.world

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.user.TokenProfile
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
import app.mcorg.presentation.handler.handlePipeline
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

    handlePipeline(
        onSuccess = { tabData ->
            if (request.headers["HX-Request"] == "true" && tab != null) {
                respondHtml(createHTML().div("world-project-content") {
                    worldProjectContent(tabData)
                })
            } else {
                respondHtml(worldPage(tabData, notifications))
            }
        }
    ) {
        GetTabDataStep(worldId, user).run(tab)
    }
}

private data class GetTabDataStep(val worldId: Int, val user: TokenProfile) : Step<String?, AppFailure, WorldPageTabData> {
    override suspend fun process(input: String?): Result<AppFailure, WorldPageTabData> {
        return when (input) {
            "roadmap" -> {
                val worldMember = GetWorldMemberStep(worldId).process(user.id)
                val world = GetWorldStep.process(worldId)
                val roadmap = GetWorldRoadMapStep(worldId).process(Unit)
                if (worldMember is Result.Failure) return Result.failure(worldMember.error)
                if (world is Result.Failure) return Result.failure(world.error)
                if (roadmap is Result.Failure) return Result.failure(roadmap.error)

                Result.success(WorldPageTabData.RoadmapData(user, world.getOrNull()!!, worldMember.getOrNull()!!, roadmap.getOrNull()!!))
            }
            "kanban" -> {
                val worldMember = GetWorldMemberStep(worldId).process(user.id)
                val world = GetWorldStep.process(worldId)
                if (worldMember is Result.Failure) return Result.failure(worldMember.error)
                if (world is Result.Failure) return Result.failure(world.error)
                Result.success(WorldPageTabData.KanbanData(user, world.getOrNull()!!, worldMember.getOrNull()!!))
            }
            else -> {
                getProjectTabData(worldId, user)
                    .map { (world, member, projects) -> WorldPageTabData.ProjectsData(user, world, member, projects) }
            }
        }
    }
}

private suspend fun getProjectTabData(worldId: Int, user: TokenProfile): Result<AppFailure, Triple<World, WorldMember, List<Project>>> {
    val world = GetWorldStep.process(worldId)
    val worldMember = GetWorldMemberStep(worldId).process(user.id)
    val projects = SearchProjectsStep.process(SearchProjectsInput(worldId))

    if (world is Result.Failure) return Result.failure(world.error)
    if (worldMember is Result.Failure) return Result.failure(worldMember.error)
    if (projects is Result.Failure) return Result.failure(projects.error)

    return Result.success(Triple(world.getOrNull()!!, worldMember.getOrNull()!!, projects.getOrNull()!!))
}
