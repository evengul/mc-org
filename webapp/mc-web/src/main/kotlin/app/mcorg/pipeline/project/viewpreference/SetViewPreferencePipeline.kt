package app.mcorg.pipeline.project.viewpreference

import app.mcorg.pipeline.project.commonsteps.SetViewPreferenceInput
import app.mcorg.pipeline.project.commonsteps.SetViewPreferenceStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondEmptyHtml
import io.ktor.server.application.*
import io.ktor.server.request.*

suspend fun ApplicationCall.handleSetViewPreference() {
    val parameters = this.receiveParameters()
    val userId = this.getUser().id
    val projectId = this.getProjectId()
    val preference = parameters["preference"] ?: ""

    handlePipeline(
        onSuccess = { respondEmptyHtml() }
    ) {
        SetViewPreferenceStep.run(SetViewPreferenceInput(userId, projectId, preference))
    }
}
