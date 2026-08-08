package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.pages.roadmapPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

/**
 * `GET /worlds/{worldId}/roadmap` (MCO-288) — the world's derived project sequence.
 *
 * Read-only, so world membership (enforced by the route's plugins) is the whole
 * authorization story; the admin check only decides what the header offers.
 */
suspend fun ApplicationCall.handleGetWorldRoadmap() {
    val user = getUser()
    val worldId = getWorldId()
    val isAdmin = ValidateWorldMemberRole<Unit>(user, Role.ADMIN, worldId).process(Unit) is Result.Success

    handlePipeline(
        onSuccess = { roadmap: Roadmap ->
            respondHtml(roadmapPage(user, roadmap, isWorldAdmin = isAdmin))
        }
    ) {
        GetWorldRoadMapStep(worldId).run(Unit)
    }
}
