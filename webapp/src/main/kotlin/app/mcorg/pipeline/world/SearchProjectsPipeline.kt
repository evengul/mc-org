package app.mcorg.pipeline.world

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.getProjectsByWorldIdQuery
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.layout.alert.ALERT_CONTAINER_ID
import app.mcorg.presentation.templated.layout.alert.AlertType
import app.mcorg.presentation.templated.layout.alert.createAlert
import app.mcorg.presentation.templated.world.projectList
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.html.ul

sealed interface SearchProjectsFailure {
    object DatabaseError : SearchProjectsFailure
}

data class SearchProjectsInput(
    val worldId: Int,
    val query: String,
    val sortBy: String,
    val showCompleted: Boolean
)

suspend fun ApplicationCall.handleSearchProjects() {
    val worldId = this.getWorldId()
    val parameters = this.request.queryParameters

    executeParallelPipeline<SearchProjectsFailure, Pair<List<Project>, Int>>(
        onSuccess = { (projects, projectCount) ->
            respondHtml(createHTML().ul {
                projectList(projects)
            } + createHTML().p("subtle") {
                hxOutOfBands("true")
                id = "world-projects-count"
                +"Showing ${projects.size} of $projectCount projects."
            })
        },
        onFailure = {
            respondHtml(createHTML().li {
                hxOutOfBands("#$ALERT_CONTAINER_ID")
                createAlert(
                    id = "search-projects-error",
                    type = AlertType.ERROR,
                    title = "Error while searching projects",
                    message = "An error occurred while searching for projects. Please try again later.",
                    autoClose = true
                )
            })
        }
    ) {
        val searchResult = pipeline("searchResults", parameters, Pipeline.create<SearchProjectsFailure, Parameters>()
            .pipe(ValidateSearchProjectsInputStep(worldId))
            .pipe(SearchProjectsStep))

        val countResult = pipeline("projectCount", worldId, Pipeline.create<SearchProjectsFailure, Int>()
            .pipe(CountProjectsInWorldStep))

        merge("merged", searchResult, countResult) { searchProjects, projectCount ->
            Result.success(searchProjects to projectCount)
        }
    }
}

private data class ValidateSearchProjectsInputStep(val worldId: Int) : Step<Parameters, SearchProjectsFailure, SearchProjectsInput> {
    override suspend fun process(input: Parameters): Result<SearchProjectsFailure, SearchProjectsInput> {
        val query = input["query"] ?: ""
        val showCompleted = input["showCompleted"] == "on"
        val sortBy = input["sortBy"]?.takeIf { it in setOf("name_asc", "lastModified_desc") } ?: "lastModified_desc"

        return Result.success(SearchProjectsInput(
            worldId = worldId,
            query = query,
            showCompleted = showCompleted,
            sortBy = sortBy
        ))
    }
}

private data object SearchProjectsStep : Step<SearchProjectsInput, SearchProjectsFailure, List<Project>> {
    override suspend fun process(input: SearchProjectsInput): Result<SearchProjectsFailure, List<Project>> {
        val sortQuery = when(input.sortBy) {
            "name_asc" -> "p.name ASC, p.updated_at DESC"
            "lastModified_desc" -> "p.updated_at DESC, p.name ASC"
            else -> "p.updated_at DESC, p.name ASC"
        }
        return DatabaseSteps.query<SearchProjectsInput, SearchProjectsFailure, List<Project>>(
            getProjectsByWorldIdQuery(sortQuery),
            parameterSetter = { statement, searchInput ->
                statement.setInt(1, searchInput.worldId)

                val normalizedQuery = searchInput.query.trim().lowercase()
                statement.setString(2, normalizedQuery)
                statement.setString(3, normalizedQuery)
                statement.setString(4, normalizedQuery)

                statement.setBoolean(5, searchInput.showCompleted)
            },
            errorMapper = { SearchProjectsFailure.DatabaseError },
            resultMapper = { it.toProjects() }
        ).process(input)
    }
}

private data object CountProjectsInWorldStep : Step<Int, SearchProjectsFailure, Int> {
    override suspend fun process(input: Int): Result<SearchProjectsFailure, Int> {
        return DatabaseSteps.query<Int, SearchProjectsFailure, Int>(
            SafeSQL.select("SELECT COUNT(*) AS project_count FROM projects WHERE world_id = ?"),
            parameterSetter = { statement, worldId ->
                statement.setInt(1, worldId)
            },
            errorMapper = { SearchProjectsFailure.DatabaseError },
            resultMapper = {
                if (it.next()) {
                    it.getInt("project_count")
                } else {
                    0
                }
            }
        ).process(input)
    }
}