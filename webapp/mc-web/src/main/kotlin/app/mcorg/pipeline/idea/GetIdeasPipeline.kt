package app.mcorg.pipeline.idea

import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.idea.ideasPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*

suspend fun ApplicationCall.handleGetIdeas() {
    val user = this.getUser()
    val filters = IdeaFilterParser.parse(request.queryParameters)

    handlePipeline(
        onSuccess = {
            respondHtml(ideasPage(user = user, result = it, filters = filters))
        }
    ) {
        SearchIdeasStep.run(filters)
    }
}
