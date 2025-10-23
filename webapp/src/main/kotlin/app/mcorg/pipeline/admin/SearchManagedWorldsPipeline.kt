package app.mcorg.pipeline.admin

import app.mcorg.domain.model.admin.ManagedWorld
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.admin.AdminTable
import app.mcorg.presentation.templated.admin.paginationInfo
import app.mcorg.presentation.templated.admin.worldRows
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlinx.html.tbody
import kotlinx.html.td

sealed interface SearchManagedWorldsFailures {
    object DatabaseError : SearchManagedWorldsFailures
}

suspend fun ApplicationCall.handleSearchManagedWorlds() {
    val input = GetManagedWorldsInput(
        query = this.request.queryParameters["query"] ?: "",
        page = this.request.queryParameters["page"]?.toIntOrNull() ?: 1,
        pageSize = this.request.queryParameters["pageSize"]?.toIntOrNull() ?: 10
    )

    val worldsPipeline = Pipeline.create<SearchManagedWorldsFailures, GetManagedWorldsInput>()
        .pipe(object : Step<GetManagedWorldsInput, SearchManagedWorldsFailures, List<ManagedWorld>> {
            override suspend fun process(input: GetManagedWorldsInput): Result<SearchManagedWorldsFailures, List<ManagedWorld>> {
                return GetManagedWorldsStep.process(input)
                    .mapError { SearchManagedWorldsFailures.DatabaseError }
            }
        })

    val countPipeline = Pipeline.create<SearchManagedWorldsFailures, String>()
        .pipe(object : Step<String, SearchManagedWorldsFailures, Int> {
            override suspend fun process(input: String): Result<SearchManagedWorldsFailures, Int> {
                return CountManagedWorldsStep.process(input)
                    .mapError { SearchManagedWorldsFailures.DatabaseError }
            }
        })

    executeParallelPipeline(
        onSuccess = { respondHtml(createHTML().tbody {
            worldRows(it.first)
        } + createHTML().td {
            hxOutOfBands("innerHTML:#pagination-info-worlds")
            div {
                paginationInfo(it.second, input.page, AdminTable.WORLDS)
            }
        }) },
        onFailure = {
            respond(HttpStatusCode.InternalServerError)
        }
    ) {
        val worlds = pipeline("worlds", input, worldsPipeline)
        val count = pipeline("count", input.query, countPipeline)

        merge("data", worlds, count) { w, c ->
            Result.success(w to c)
        }
    }
}