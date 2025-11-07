package app.mcorg.pipeline.admin

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.admin.commonsteps.CountManagedUsersStep
import app.mcorg.pipeline.admin.commonsteps.GetManagedUsersInput
import app.mcorg.pipeline.admin.commonsteps.GetManagedUsersStep
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.executeParallelPipeline
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

    val usersPipeline = Pipeline.create<AppFailure.DatabaseError, GetManagedUsersInput>()
        .pipe(GetManagedUsersStep)

    val countPipeline = Pipeline.create<AppFailure.DatabaseError, String>()
        .pipe(CountManagedUsersStep)

    executeParallelPipeline(
        onSuccess = { respondHtml(createHTML().tbody {
            userRows(it.first)
        } + createHTML().td {
            hxOutOfBands("innerHTML:#pagination-info-users")
            div {
                paginationInfo(it.second, page, AdminTable.USERS)
            }
        }) }
    ) {
        val users = pipeline("users", GetManagedUsersInput(query, page, pageSize), usersPipeline)
        val count = pipeline("count", query, countPipeline)

        merge("data", users, count) { u, c ->
            Result.success(u to c)
        }
    }
}