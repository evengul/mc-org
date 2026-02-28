package app.mcorg.pipeline.admin

import app.mcorg.pipeline.admin.commonsteps.CountManagedUsersStep
import app.mcorg.pipeline.admin.commonsteps.GetManagedUsersInput
import app.mcorg.pipeline.admin.commonsteps.GetManagedUsersStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.admin.AdminTable
import app.mcorg.presentation.templated.admin.paginationInfo
import app.mcorg.presentation.templated.admin.userRows
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlinx.html.tbody
import kotlinx.html.td

suspend fun ApplicationCall.handleSearchManagedUsers() {
    val query = this.request.queryParameters["query"] ?: ""
    val page = this.request.queryParameters["page"]?.toIntOrNull() ?: 1
    val pageSize = this.request.queryParameters["pageSize"]?.toIntOrNull() ?: 10

    handlePipeline(
        onSuccess = { (users, count) ->
            respondHtml(createHTML().tbody {
                userRows(users)
            } + createHTML().td {
                hxOutOfBands("innerHTML:#pagination-info-users")
                div {
                    paginationInfo(count, page, AdminTable.USERS)
                }
            })
        }
    ) {
        val (users, count) = parallel(
            { GetManagedUsersStep.run(GetManagedUsersInput(query, page, pageSize)) },
            { CountManagedUsersStep.run(query) },
        )
        users to count
    }
}
