package app.mcorg.pipeline.admin

import app.mcorg.domain.pipeline.Step
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.admin.worldRows
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.html.stream.createHTML
import kotlinx.html.tbody

sealed interface SearchManagedWorldsFailures {
    object DatabaseError : SearchManagedWorldsFailures
}

suspend fun ApplicationCall.handleSearchManagedWorlds() {
    val query = this.request.queryParameters["query"] ?: ""

    executePipeline(
        onSuccess = { respondHtml(createHTML().tbody {
            worldRows(it)
        }) },
        onFailure = {
            respond(HttpStatusCode.InternalServerError)
        }
    ) {
        step(Step.value(GetManagedWorldsInput(query)))
            .step(GetManagedWorldsStep)
    }
}