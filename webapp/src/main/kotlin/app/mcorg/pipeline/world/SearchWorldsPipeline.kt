package app.mcorg.pipeline.world

import app.mcorg.domain.model.world.World
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.home.worldList
import app.mcorg.presentation.templated.layout.alert.ALERT_CONTAINER_ID
import app.mcorg.presentation.templated.layout.alert.AlertType
import app.mcorg.presentation.templated.layout.alert.createAlert
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.html.ul

sealed interface SearchWorldsFailure {
    object DatabaseError : SearchWorldsFailure
}

suspend fun ApplicationCall.handleSearchWorlds() {
    val userId = this.getUser().id
    val parameters = this.request.queryParameters

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
        val getWorlds = pipeline("getWorlds", parameters, Pipeline.create<SearchWorldsFailure, Parameters>()
            .pipe(ValidateSearchWorldsInputStep)
            .pipe(SearchWorldsStep(userId)))

        val countWorlds = pipeline("countWorlds", userId, Pipeline.create<SearchWorldsFailure, Int>()
            .pipe(CountPermittedWorldsStep))

        merge("searchWorldsData", getWorlds, countWorlds) { worlds, totalCount ->
            Result.success(worlds to totalCount)
        }
    }
}

private object ValidateSearchWorldsInputStep : Step<Parameters, SearchWorldsFailure, String> {
    override suspend fun process(input: Parameters): Result<SearchWorldsFailure, String> {
        val result = ValidationSteps.optional("query").process(input)
            .getOrNull() ?: ""

        return Result.Success(result)
    }
}

private data class SearchWorldsStep(val userId: Int) : Step<String, SearchWorldsFailure, List<World>> {
    override suspend fun process(input: String): Result<SearchWorldsFailure, List<World>> {
        return GetPermittedWorldsStep.process(
            GetPermittedWorldsInput(
                userId = userId,
                query = input
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

