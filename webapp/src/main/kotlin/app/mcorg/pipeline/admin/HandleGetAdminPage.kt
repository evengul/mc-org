package app.mcorg.pipeline.admin

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.notification.GetUnreadNotificationCountStep
import app.mcorg.presentation.mockdata.MockManagedUsers
import app.mcorg.presentation.mockdata.MockManagedWorlds
import app.mcorg.presentation.templated.admin.adminPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

suspend fun ApplicationCall.handleGetAdminPage() {
    val user = getUser() // Use real authenticated user instead of mock
    val users = MockManagedUsers.getMockedManagedUsers() // TODO: Replace with real admin data
    val worlds = MockManagedWorlds.getManagedWorlds() // TODO: Replace with real admin data

    val unreadCount = when (val unreadCountResult = GetUnreadNotificationCountStep.process(user.id)) {
        is Result.Success -> unreadCountResult.value
        is Result.Failure -> 0
    }

    respondHtml(
        adminPage(user, users, worlds, unreadCount)
    )
}