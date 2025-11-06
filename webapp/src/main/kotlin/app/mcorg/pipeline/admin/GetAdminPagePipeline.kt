package app.mcorg.pipeline.admin

import app.mcorg.domain.model.admin.ManagedUser
import app.mcorg.domain.model.admin.ManagedWorld
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.admin.commonsteps.*
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.notification.getUnreadNotificationsOrZero
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.templated.admin.adminPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*

private data class Success(
    val users: List<ManagedUser>,
    val userCount: Int,
    val worlds: List<ManagedWorld>,
    val worldCount: Int,
    val unreadNotifications: Int
)

suspend fun ApplicationCall.handleGetAdminPage() {
    val user = getUser()

    val userPipe = Pipeline.create<AppFailure.DatabaseError, Int>()
        .map { GetManagedUsersInput() }
        .pipe(GetManagedUsersStep)

    val worldsPipe = Pipeline.create<AppFailure.DatabaseError,  Int>()
        .map { GetManagedWorldsInput() }
        .pipe(GetManagedWorldsStep)

    val userCountPipe = Pipeline.create<AppFailure.DatabaseError, Unit>()
        .map { "" }
        .pipe(CountManagedUsersStep)

    val worldCountPipe = Pipeline.create<AppFailure.DatabaseError, Unit>()
        .map { "" }
        .pipe(CountManagedWorldsStep)

    val unreadNotifications = getUnreadNotificationsOrZero(user.id)

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

        val userCount = pipeline("userCount", Unit, userCountPipe)
        val worldCount = pipeline("worldCount", Unit, worldCountPipe)

        val data = merge("data", users, worlds) { u, w ->
            Result.success(u to w)
        }
        val count = merge("count", userCount, worldCount) { uc, wc ->
            Result.success(uc to wc)
        }

        merge("final", data, count) { d, c ->
            val (u, w) = d
            val (uc, wc) = c
            Result.success(Success(
                users = u,
                userCount = uc,
                worlds = w,
                worldCount = wc,
                unreadNotifications = unreadNotifications
            ))
        }
    }
}