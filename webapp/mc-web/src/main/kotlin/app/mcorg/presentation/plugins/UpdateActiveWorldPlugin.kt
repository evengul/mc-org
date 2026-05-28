package app.mcorg.presentation.plugins

import app.mcorg.domain.pipeline.pipelineResult
import app.mcorg.pipeline.auth.commonsteps.AddCookieStep
import app.mcorg.pipeline.auth.commonsteps.CreateTokenStep
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.utils.getHost
import app.mcorg.presentation.utils.getUser
import io.ktor.server.application.createRouteScopedPlugin

val UpdateActiveWorldPlugin = createRouteScopedPlugin("UpdateActiveWorldPlugin") {
    onCall { call ->
        val worldId = call.parameters["worldId"]?.toIntOrNull() ?: return@onCall
        val user = call.getUser()
        if (user.activeWorldId != worldId) {
            pipelineResult<AppFailure, Unit> {
                val token = CreateTokenStep.run(user.copy(activeWorldId = worldId))
                AddCookieStep(call.response.cookies, call.getHost() ?: "false").run(token)
            }
        }
    }
}
