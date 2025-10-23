package app.mcorg.pipeline.admin

import app.mcorg.domain.pipeline.Step
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.admin.userRows
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.html.stream.createHTML
import kotlinx.html.tbody

sealed interface SearchManagedUsersFailures {
    object DatabaseError : SearchManagedUsersFailures
}

suspend fun ApplicationCall.handleSearchManagedUsers() {
    val query = this.request.queryParameters["query"] ?: ""

    executePipeline(
        onSuccess = { respondHtml(createHTML().tbody {
            userRows(it)
        }) },
        onFailure = {
            respond(HttpStatusCode.InternalServerError)
        }
    ) {
        step(Step.value(GetManagedUsersInput(query)))
            .step(GetManagedUsersStep)
    }
}