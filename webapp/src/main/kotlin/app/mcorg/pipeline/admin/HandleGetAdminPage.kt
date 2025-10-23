package app.mcorg.pipeline.admin

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.notification.GetUnreadNotificationCountStep
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.templated.admin.adminPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

interface HandleGetAdminPageFailures {
    object DatabaseError : HandleGetAdminPageFailures
}

suspend fun ApplicationCall.handleGetAdminPage() {
    val user = getUser()

    val userPipe = Pipeline.create<HandleGetAdminPageFailures, Int>()
        .map { GetManagedUsersInput("") }
        .pipe(GetManagedUsersStep)

    val worldsPipe = Pipeline.create<HandleGetAdminPageFailures,  Int>()
        .map { GetManagedWorldsInput("") }
        .pipe(GetManagedWorldsStep)

    val notificationsPipe = Pipeline.create<HandleGetAdminPageFailures, Int>()
        .pipe(object : Step<Int, HandleGetAdminPageFailures, Int> {
            override suspend fun process(input: Int): Result<HandleGetAdminPageFailures, Int> {
                return when (val result = GetUnreadNotificationCountStep.process(input)) {
                    is Result.Success -> Result.success(result.value)
                    is Result.Failure -> Result.success(0)
                }
            }
        })

    executeParallelPipeline(
        onSuccess = { (users, worlds, notifications) -> respondHtml(adminPage(user, users, worlds, notifications))},
        onFailure = { respondHtml("A system error occurred.") }
    ) {
        val users = pipeline("users", user.id, userPipe)
        val worlds = pipeline("worlds", user.id, worldsPipe)
        val notifications = pipeline("notifications", user.id, notificationsPipe)
        merge("data", users, worlds, notifications) { u, w, n ->
            Result.success(Triple(u, w, n))
        }
    }
}