package app.mcorg.pipeline.world

import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Step
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

suspend fun ApplicationCall.handleDeleteWorld() {
    val user = this.getUser()
    val worldId = this.getWorldId()

    executePipeline(
        onFailure = { respond(HttpStatusCode.InternalServerError) },
        onSuccess = { clientRedirect(Link.Home.to) }
    ) {
        step(Step.value(worldId))
            .step(ValidateWorldMemberRole(user, Role.OWNER)) // TODO: Create a WorldOwnerPlugin
            .step(DeleteWorldStep)
    }
}