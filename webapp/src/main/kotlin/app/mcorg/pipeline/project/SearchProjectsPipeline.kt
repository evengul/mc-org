package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.project.commonsteps.SearchProjectsInput
import app.mcorg.pipeline.project.commonsteps.SearchProjectsStep
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.world.projectList
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.html.ul

suspend fun ApplicationCall.handleSearchProjects() {
    val worldId = this.getWorldId()
    val parameters = this.request.queryParameters

    executeParallelPipeline(
        onSuccess = { (projects, projectCount) ->
            respondHtml(createHTML().ul {
                projectList(projects)
            } + createHTML().p("subtle") {
                hxOutOfBands("true")
                id = "world-projects-count"
                +"Showing ${projects.size} of $projectCount projects."
            })
        }
    ) {
        val searchResult = pipeline("searchResults", parameters, Pipeline.create<AppFailure.DatabaseError, Parameters>()
            .pipe(ValidateSearchProjectsInputStep(worldId))
            .pipe(SearchProjectsStep))


        val countResult = singleStep("projectCount", worldId, CountProjectsInWorldStep)

        merge("merged", searchResult, countResult) { searchProjects, projectCount ->
            Result.success(searchProjects to projectCount)
        }
    }
}

private data class ValidateSearchProjectsInputStep(val worldId: Int) : Step<Parameters, Nothing, SearchProjectsInput> {
    override suspend fun process(input: Parameters): Result<Nothing, SearchProjectsInput> {
        val query = input["query"] ?: ""
        val showCompleted = input["showCompleted"] == "on"
        val sortBy = input["sortBy"]?.takeIf { it in setOf("name_asc", "lastModified_desc") } ?: "lastModified_desc"

        return Result.success(
            SearchProjectsInput(
                worldId = worldId,
                query = query,
                showCompleted = showCompleted,
                sortBy = sortBy
            )
        )
    }
}

private val CountProjectsInWorldStep = DatabaseSteps.query<Int, Int>(
    SafeSQL.select("SELECT COUNT(*) AS project_count FROM projects WHERE world_id = ?"),
    parameterSetter = { statement, worldId ->
        statement.setInt(1, worldId)
    },
    resultMapper = {
        if (it.next()) {
            it.getInt("project_count")
        } else {
            0
        }
    }
)