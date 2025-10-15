package app.mcorg.pipeline.idea

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.notification.GetUnreadNotificationCountStep
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.mockdata.IdeaMockData
import app.mcorg.presentation.templated.idea.ideaList
import app.mcorg.presentation.templated.idea.ideasPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

suspend fun ApplicationCall.handleGetIdeas() {
    val user = this.getUser()

    val getIdeasPipeline = Pipeline.create<Nothing, Unit>()
        .pipe(Step.value(IdeaMockData.allIdeas))

    val getNotificationCountsPipeline = Pipeline.create<Nothing, Int>()
        .pipe(Step.value(user.id))
        .pipe(object : Step<Int, Nothing, Int> {
            override suspend fun process(input: Int): Result<Nothing, Int> {
                return when (val result = GetUnreadNotificationCountStep.process(input)) {
                    is Result.Success -> Result.success(result.value)
                    is Result.Failure -> Result.success(0)
                }
            }
        })
        .recover { Result.success(0) }

    executeParallelPipeline(
        onSuccess = {
            respondHtml(ideasPage(
                user = user,
                ideas = it.first,
                unreadNotifications = it.second
            ))
        },
        onFailure = {
            respond(HttpStatusCode.InternalServerError, "Failed to get ideas")
        }
    ) {
        val ideas = pipeline("ideas", Unit, getIdeasPipeline)
        val unreadCount = pipeline("unreadCount", user.id, getNotificationCountsPipeline)

        merge("ideasWithCount", ideas, unreadCount) { ideas, count ->
            Result.success(ideas to count)
        }
    }
}