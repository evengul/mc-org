package app.mcorg.pipeline.admin

import app.mcorg.pipeline.admin.commonsteps.CountManagedWorldsStep
import app.mcorg.pipeline.admin.commonsteps.GetManagedWorldsInput
import app.mcorg.pipeline.admin.commonsteps.GetManagedWorldsStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.admin.AdminTable
import app.mcorg.presentation.templated.admin.paginationInfo
import app.mcorg.presentation.templated.admin.worldRows
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
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

    handlePipeline(
        onSuccess = { (worlds, count) ->
            respondHtml(createHTML().tbody {
                worldRows(worlds)
            } + createHTML().td {
                hxOutOfBands("innerHTML:#pagination-info-worlds")
                div {
                    paginationInfo(count, input.page, AdminTable.WORLDS)
                }
            })
        }
    ) {
        val (worlds, count) = parallel(
            { GetManagedWorldsStep.run(input) },
            { CountManagedWorldsStep.run(input.query) },
        )
        worlds to count
    }
}
