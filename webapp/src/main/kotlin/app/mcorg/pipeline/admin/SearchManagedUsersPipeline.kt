package app.mcorg.pipeline.admin

import app.mcorg.domain.model.admin.ManagedUser
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.admin.AdminTable
import app.mcorg.presentation.templated.admin.paginationInfo
import app.mcorg.presentation.templated.admin.userRows
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlinx.html.tbody
import kotlinx.html.td

sealed interface SearchManagedUsersFailures {
    object DatabaseError : SearchManagedUsersFailures
}

suspend fun ApplicationCall.handleSearchManagedUsers() {
    val query = this.request.queryParameters["query"] ?: ""
    val page = this.request.queryParameters["page"]?.toIntOrNull() ?: 1
    val pageSize = this.request.queryParameters["pageSize"]?.toIntOrNull() ?: 10

    val usersPipeline = Pipeline.create<SearchManagedUsersFailures, GetManagedUsersInput>()
        .pipe(object : Step<GetManagedUsersInput, SearchManagedUsersFailures, List<ManagedUser>> {
            override suspend fun process(input: GetManagedUsersInput): Result<SearchManagedUsersFailures, List<ManagedUser>> {
                return GetManagedUsersStep.process(input)
                    .mapError { SearchManagedUsersFailures.DatabaseError }
            }
        })

    val countPipeline = Pipeline.create<SearchManagedUsersFailures, String>()
        .pipe(object : Step<String, SearchManagedUsersFailures, Int> {
            override suspend fun process(input: String): Result<SearchManagedUsersFailures, Int> {
                return CountManagedUsersStep.process(input)
                    .mapError { SearchManagedUsersFailures.DatabaseError }
            }
        })

    executeParallelPipeline(
        onSuccess = { respondHtml(createHTML().tbody {
            userRows(it.first)
        } + createHTML().td {
            hxOutOfBands("innerHTML:#pagination-info-users")
            div {
                paginationInfo(it.second, page, AdminTable.USERS)
            }
        }) },
        onFailure = {
            respond(HttpStatusCode.InternalServerError)
        }
    ) {
        val users = pipeline("users", GetManagedUsersInput(query, page, pageSize), usersPipeline)
        val count = pipeline("count", query, countPipeline)

        merge("data", users, count) { u, c ->
            Result.success(u to c)
        }
    }
}