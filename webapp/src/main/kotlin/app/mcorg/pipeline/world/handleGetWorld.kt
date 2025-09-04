package app.mcorg.pipeline.world

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.user.WorldMember
import app.mcorg.domain.model.world.World
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.failure.HandleGetWorldFailure
import app.mcorg.pipeline.notification.GetUnreadNotificationCountStep
import app.mcorg.pipeline.project.getProjectsByWorldIdQuery
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.templated.world.kanbanView
import app.mcorg.presentation.templated.world.projectList
import app.mcorg.presentation.templated.world.roadmapView
import app.mcorg.presentation.templated.world.worldPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlinx.html.ul

data class GetWorldSuccess(
    val world: World,
    val worldMember: WorldMember,
    val projects: List<Project>,
    val unreadCount: Int,
)

suspend fun ApplicationCall.handleGetWorld() {
    val user = this.getUser()
    val worldId = this.getWorldId()

    val tab = request.queryParameters["tab"]

    val projectsQueryStep = DatabaseSteps.query<Int, HandleGetWorldFailure, List<Project>>(
        getProjectsByWorldIdQuery,
        parameterSetter = { statement, input ->
            statement.setInt(1, input)
        },
        errorMapper = { HandleGetWorldFailure.SystemError("A system error occurred while fetching projects for the world.") },
        resultMapper = { it.toProjects() }
    )

    val getWorldPipeline = Pipeline.create<HandleGetWorldFailure, Int>()
        .pipe(worldQueryStep)

    val getWorldMemberPipeline = Pipeline.create<DatabaseFailure, WorldMemberQueryInput>()
        .pipe(worldMemberQueryStep)
        .mapFailure { HandleGetWorldFailure.SystemError("A system error occurred while fetching the world member") }

    val getProjectsPipeline = Pipeline.create<HandleGetWorldFailure, Int>()
        .pipe(projectsQueryStep)

    val notificationCountPipeline = Pipeline.create<HandleGetWorldFailure, Int>()
        .pipe(object : app.mcorg.domain.pipeline.Step<Int, HandleGetWorldFailure, Int> {
            override suspend fun process(input: Int): Result<HandleGetWorldFailure, Int> {
                return when (val result = GetUnreadNotificationCountStep.process(input)) {
                    is Result.Success -> Result.success(result.value)
                    is Result.Failure -> Result.success(0) // Gracefully handle notification count errors
                }
            }
        })

    executeParallelPipeline(
        onSuccess = { (world, worldMember, projects, unreadCount) ->
            if (request.headers["HX-Request"] == "true" && tab != null) {
                handleGetTab(tab, projects)
            } else {
                respondHtml(worldPage(user, world, worldMember, projects, tab, unreadCount))
            }
        },
        onFailure = { handleWorldFailure(it) }
    ) {
        val getWorld = pipeline("world", worldId, getWorldPipeline)
        val getProjects = pipeline("projects", worldId, getProjectsPipeline)
        val getUnreadCount = pipeline("unreadCount", user.id, notificationCountPipeline)
        val getWorldMember = pipeline("worldMember", WorldMemberQueryInput(user.id, worldId), getWorldMemberPipeline)

        val worldAndMember = merge("worldAndMember", getWorld, getWorldMember) { world, member ->
            Result.success(Pair(world, member))
        }

        val unreadAndProjects = merge("unreadAndProjects", getUnreadCount, getProjects) { unreadCount, projects ->
            Result.success(Pair(unreadCount, projects))
        }

        merge("worldData", worldAndMember, unreadAndProjects) { worldAndMember, unreadAndProjects ->
            Result.success(GetWorldSuccess(worldAndMember.first, worldAndMember.second, unreadAndProjects.second, unreadAndProjects.first))
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