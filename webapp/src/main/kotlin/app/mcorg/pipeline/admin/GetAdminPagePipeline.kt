package app.mcorg.pipeline.admin

import app.mcorg.domain.model.admin.ManagedUser
import app.mcorg.domain.model.admin.ManagedWorld
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.admin.commonsteps.*
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

    val unreadNotifications = getUnreadNotificationsOrZero(user.id)

    executeParallelPipeline(
        onSuccess = { result: Success -> respondHtml(adminPage(
            user,
            result.users,
            result.worlds,
            result.userCount,
            result.worldCount,
            result.unreadNotifications))},
    ) {
        val users = singleStep("users", GetManagedUsersInput(), GetManagedUsersStep)
        val worlds = singleStep("worlds", GetManagedWorldsInput(), GetManagedWorldsStep)

        val userCount = singleStep("userCount", "", CountManagedUsersStep)
        val worldCount = singleStep("worldCount", "", CountManagedWorldsStep)

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