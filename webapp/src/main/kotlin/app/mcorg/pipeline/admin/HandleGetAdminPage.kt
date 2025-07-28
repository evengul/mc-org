package app.mcorg.pipeline.admin

import app.mcorg.presentation.mockdata.MockManagedUsers
import app.mcorg.presentation.mockdata.MockManagedWorlds
import app.mcorg.presentation.mockdata.MockUsers
import app.mcorg.presentation.templated.admin.adminPage
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

suspend fun ApplicationCall.handleGetAdminPage() {
    val user = MockUsers.Evegul.tokenProfile()
    val users = MockManagedUsers.getMockedManagedUsers()
    val worlds = MockManagedWorlds.getManagedWorlds()

    respondHtml(
        adminPage(user, users, worlds)
    )
}