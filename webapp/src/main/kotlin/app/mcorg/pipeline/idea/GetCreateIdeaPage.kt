package app.mcorg.pipeline.idea

import app.mcorg.pipeline.minecraft.GetSupportedVersionsStep
import app.mcorg.pipeline.notification.getUnreadNotificationsOrZero
import app.mcorg.presentation.templated.idea.createwizard.createIdeaPage
import app.mcorg.presentation.templated.idea.createwizard.createIdeaStageContent
import app.mcorg.presentation.templated.idea.createwizard.toCreateIdeaDataHolder
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.hxTarget
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.form
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetCreateIdeaPage() {
    val user = this.getUser()
    val notifications = getUnreadNotificationsOrZero(user.id)
    val supportedVersions = GetSupportedVersionsStep.getSupportedVersions()

    val data = request.queryParameters.toCreateIdeaDataHolder(user.minecraftUsername, supportedVersions)

    if (request.headers["HX-Request"] == "true") {
        hxTarget("#create-idea-form")
        respondHtml(createHTML().form {
            createIdeaStageContent(data, supportedVersions)
        })
        return
    }

    respondHtml(createIdeaPage(
        user,
        notifications,
        supportedVersions,
        data
    ))
}