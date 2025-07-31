package app.mcorg.pipeline.world

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.world.World
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.failure.HandleGetWorldFailure
import app.mcorg.pipeline.project.getProjectsByWorldIdQuery
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.mockdata.MockUsers
import app.mcorg.presentation.templated.world.kanbanView
import app.mcorg.presentation.templated.world.projectList
import app.mcorg.presentation.templated.world.roadmapView
import app.mcorg.presentation.templated.world.worldPage
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlinx.html.ul

suspend fun ApplicationCall.handleGetWorld() {
    val user = MockUsers.Evegul.tokenProfile()

    val tab = request.queryParameters["tab"]

    val projectsQueryStep = DatabaseSteps.query<World, HandleGetWorldFailure, List<Project>>(
        getProjectsByWorldIdQuery,
        parameterSetter = { statement, input ->
            statement.setInt(1, input.id)
        },
        errorMapper = { HandleGetWorldFailure.SystemError("A system error occurred while fetching projects for the world.") },
        resultMapper = { it.toProjects() }
    )

    lateinit var world: World

    executeParallelPipeline(
        onSuccess = { (world, projects) ->
            if (request.headers["HX-Request"] == "true" && tab != null) {
                handleGetTab(tab, projects)
            } else {
                respondHtml(worldPage(user, world, projects, tab))
            }
        },
        onFailure = { handleWorldFailure(it) }
    ) {
        pipeline(
            id = "world-with-projects",
            input = parameters,
            pipeline = Pipeline.create<HandleGetWorldFailure, Parameters>()
                .pipe(getWorldIdStep)
                .pipe(worldQueryStep)
                .pipe(validateWorldExistsStep) { world = it }
                .pipe(projectsQueryStep)
                .pipe(Step.value { world to it })
        )
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