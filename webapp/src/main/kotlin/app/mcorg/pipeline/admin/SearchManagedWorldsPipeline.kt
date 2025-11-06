package app.mcorg.pipeline.admin

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.admin.commonsteps.CountManagedWorldsStep
import app.mcorg.pipeline.admin.commonsteps.GetManagedWorldsInput
import app.mcorg.pipeline.admin.commonsteps.GetManagedWorldsStep
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.admin.AdminTable
import app.mcorg.presentation.templated.admin.paginationInfo
import app.mcorg.presentation.templated.admin.worldRows
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlinx.html.tbody
import kotlinx.html.td

suspend fun ApplicationCall.handleSearchManagedWorlds() {
    val input = GetManagedWorldsInput(
        query = this.request.queryParameters["query"] ?: "",
        page = this.request.queryParameters["page"]?.toIntOrNull() ?: 1,
        pageSize = this.request.queryParameters["pageSize"]?.toIntOrNull() ?: 10
    )

    val worldsPipeline = Pipeline.create<AppFailure.DatabaseError, GetManagedWorldsInput>()
        .pipe(GetManagedWorldsStep)

    val countPipeline = Pipeline.create<AppFailure.DatabaseError, String>()
        .pipe(CountManagedWorldsStep)

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