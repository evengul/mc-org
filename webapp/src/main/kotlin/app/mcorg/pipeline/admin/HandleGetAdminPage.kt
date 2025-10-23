package app.mcorg.pipeline.admin

import app.mcorg.domain.model.admin.ManagedUser
import app.mcorg.domain.model.admin.ManagedWorld
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

private data class Success(
    val users: List<ManagedUser>,
    val userCount: Int,
    val worlds: List<ManagedWorld>,
    val worldCount: Int,
    val unreadNotifications: Int
)

suspend fun ApplicationCall.handleGetAdminPage() {
    val user = getUser()

    val userPipe = Pipeline.create<HandleGetAdminPageFailures, Int>()
        .map { GetManagedUsersInput() }
        .pipe(GetManagedUsersStep)

    val worldsPipe = Pipeline.create<HandleGetAdminPageFailures,  Int>()
        .map { GetManagedWorldsInput() }
        .pipe(GetManagedWorldsStep)

    val userCountPipe = Pipeline.create<HandleGetAdminPageFailures, Unit>()
        .pipe(object : Step<Unit, HandleGetAdminPageFailures, Int> {
            override suspend fun process(input: Unit): Result<HandleGetAdminPageFailures, Int> {
                return CountManagedUsersStep.process("").mapError { HandleGetAdminPageFailures.DatabaseError }
            }
        })

    val worldCountPipe = Pipeline.create<HandleGetAdminPageFailures, Unit>()
        .pipe(object : Step<Unit, HandleGetAdminPageFailures, Int> {
            override suspend fun process(input: Unit): Result<HandleGetAdminPageFailures, Int> {
                return CountManagedWorldsStep.process("").mapError { HandleGetAdminPageFailures.DatabaseError }
            }
        })

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
        onSuccess = { result -> respondHtml(adminPage(
            user,
            result.users,
            result.worlds,
            result.userCount,
            result.worldCount,
            result.unreadNotifications))},
        onFailure = { respondHtml("A system error occurred.") }
    ) {
        val users = pipeline("users", user.id, userPipe)
        val worlds = pipeline("worlds", user.id, worldsPipe)
        val notifications = pipeline("notifications", user.id, notificationsPipe)

        val userCount = pipeline("userCount", Unit, userCountPipe)
        val worldCount = pipeline("worldCount", Unit, worldCountPipe)

        val data = merge("data", users, worlds, notifications) { u, w, n ->
            Result.success(Triple(u, w, n))
        }
        val count = merge("count", userCount, worldCount) { uc, wc ->
            Result.success(uc to wc)
        }

        merge("final", data, count) { d, c ->
            val (u, w, n) = d
            val (uc, wc) = c
            Result.success(Success(
                users = u,
                userCount = uc,
                worlds = w,
                worldCount = wc,
                unreadNotifications = n
            ))
        }
    }
}