package app.mcorg.presentation.plugins

import app.mcorg.pipeline.pipelineResult
import app.mcorg.pipeline.auth.commonsteps.AddCookieStep
import app.mcorg.pipeline.auth.commonsteps.CreateTokenStep
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.utils.getHost
import app.mcorg.presentation.utils.getUser
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.isHandled

val UpdateActiveWorldPlugin = createRouteScopedPlugin("UpdateActiveWorldPlugin") {
    onCall { call ->
        // A refused call (unknown world, non-member) must not move the active world.
        if (call.isHandled) return@onCall
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
