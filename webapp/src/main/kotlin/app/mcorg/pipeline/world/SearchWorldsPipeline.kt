package app.mcorg.pipeline.world

import app.mcorg.domain.model.world.World
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.home.worldList
import app.mcorg.presentation.templated.layout.alert.ALERT_CONTAINER_ID
import app.mcorg.presentation.templated.layout.alert.AlertType
import app.mcorg.presentation.templated.layout.alert.createAlert
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.html.ul

sealed interface SearchWorldsFailure {
    object DatabaseError : SearchWorldsFailure
}

data class SearchWorldsInput(
    val query: String,
    val sortBy: String
)

suspend fun ApplicationCall.handleSearchWorlds() {
    val userId = this.getUser().id

    val query = request.queryParameters["query"] ?: ""
    val sortBy = request.queryParameters["sortBy"]?.takeIf {
        it in setOf("name_asc", "modified_desc")
    } ?: "modified_desc"

    executeParallelPipeline(
        onSuccess = { (worlds, count) -> respondHtml(createHTML().ul {
            worldList(worlds)
        } + createHTML().p("subtle") {
            hxOutOfBands("true")
            id = "home-worlds-count"
            + "Showing ${worlds.size} of $count world${if(count == 1) "" else "s"}."
        })},
        onFailure = {
            respondHtml(createHTML().li {
                hxOutOfBands("#$ALERT_CONTAINER_ID")
                createAlert(
                    id = "search-worlds-error-alert",
                    type = AlertType.ERROR,
                    title = "Error Searching Worlds",
                    autoClose = true
                )
            })
        },
    ) {
        val getWorlds = pipeline("getWorlds", Unit, Pipeline.create<SearchWorldsFailure, Unit>()
            .map { SearchWorldsInput(query, sortBy) }
            .pipe(SearchWorldsStep(userId)))

        val countWorlds = pipeline("countWorlds", userId, Pipeline.create<SearchWorldsFailure, Int>()
            .pipe(CountPermittedWorldsStep))

        merge("searchWorldsData", getWorlds, countWorlds) { worlds, totalCount ->
            Result.success(worlds to totalCount)
        }
    }
}

private data class SearchWorldsStep(val userId: Int) : Step<SearchWorldsInput, SearchWorldsFailure, List<World>> {
    override suspend fun process(input: SearchWorldsInput): Result<SearchWorldsFailure, List<World>> {
        return GetPermittedWorldsStep.process(
            GetPermittedWorldsInput(
                userId = userId,
                query = input.query,
                sortBy = input.sortBy
            )
        ).mapError { SearchWorldsFailure.DatabaseError }
    }
}

private data object CountPermittedWorldsStep : Step<Int, SearchWorldsFailure, Int> {
    override suspend fun process(input: Int): Result<SearchWorldsFailure, Int> {
        return DatabaseSteps.query<Int, SearchWorldsFailure, Int>(
            sql = SafeSQL.select(
                """
                SELECT COUNT(distinct world_id) as total_worlds
                FROM world w
                INNER JOIN world_members wm ON w.id = wm.world_id
                WHERE wm.user_id = ?
            """.trimIndent()
            ),
            parameterSetter = { statement, inputData ->
                statement.setInt(1, inputData)
            },
            errorMapper = { SearchWorldsFailure.DatabaseError },
            resultMapper = { resultSet ->
                if (resultSet.next()) {
                    resultSet.getInt("total_worlds")
                } else {
                    0
                }
            }
        ).process(input)
    }
}

