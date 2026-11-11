package app.mcorg.presentation.handler

import app.mcorg.domain.model.user.Role
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.presentation.templated.dsl.Link
import app.mcorg.presentation.utils.getUser
import io.ktor.server.application.*
import io.ktor.server.response.*

/**
 * `/` opens the world in the token's `active_world_id`, but only while the user still belongs
 * to it. The claim lives in the JWT and is not reissued when the world is deleted or the user
 * is removed from it, so without this check `/` sent them to a 404 (MCO-597, MCO-621).
 */
suspend fun ApplicationCall.handleGetLanding() {
    val user = getUser()
    val activeWorldId = user.activeWorldId
    val stillMember = activeWorldId != null &&
        ValidateWorldMemberRole<Unit>(user, Role.MEMBER, activeWorldId).process(Unit) is Result.Success
    if (stillMember) {
        respondRedirect(Link.Worlds.world(activeWorldId).roadmap().to)
    } else {
        respondRedirect(Link.Worlds.to)
    }
}
