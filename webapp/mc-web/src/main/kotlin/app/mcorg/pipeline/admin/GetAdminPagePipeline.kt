package app.mcorg.pipeline.admin

import app.mcorg.domain.pipeline.Quadruple
import app.mcorg.pipeline.admin.commonsteps.*
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.admin.adminPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*

suspend fun ApplicationCall.handleGetAdminPage() {
    val user = getUser()

    handlePipeline(
        onSuccess = { (users, userCount, worlds, worldCount) ->
            respondHtml(adminPage(
                user,
                users,
                worlds,
                userCount,
                worldCount,
            ))
        },
    ) {
        val (users, userCount, worlds, worldCount) = parallel(
            { GetManagedUsersStep.run(GetManagedUsersInput()) },
            { CountManagedUsersStep.run("") },
            { GetManagedWorldsStep.run(GetManagedWorldsInput()) },
            { CountManagedWorldsStep.run("") },
        )
        Quadruple(users, userCount, worlds, worldCount)
    }
}
