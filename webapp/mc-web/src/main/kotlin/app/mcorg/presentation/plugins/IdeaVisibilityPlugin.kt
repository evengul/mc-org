package app.mcorg.presentation.plugins

import app.mcorg.domain.model.idea.IdeaVisibility
import app.mcorg.domain.model.idea.isVisibleTo
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.utils.getIdeaId
import app.mcorg.presentation.utils.getUser
import io.ktor.server.application.createRouteScopedPlugin

/**
 * A private idea is visible only to its creator (MCO-291).
 *
 * Installed on the whole `/ideas/{ideaId}` subtree rather than just the detail page: importing,
 * favouriting and commenting would each otherwise be a way to reach someone else's private design.
 *
 * Responds 404 rather than 403 — telling a stranger "this exists but is not yours" leaks the
 * existence of every private design.
 *
 * Must be installed *after* IdeaParamPlugin, which is what puts the id on the call.
 */
val IdeaVisibilityPlugin = createRouteScopedPlugin("IdeaVisibilityPlugin") {
    onUnansweredCall { call ->
        val ideaId = call.getIdeaId()
        val user = call.getUser()

        val result = DatabaseSteps.query<Int, Pair<String, Int>>(
            sql = SafeSQL.select("SELECT visibility, created_by FROM ideas WHERE id = ?"),
            parameterSetter = { statement, id -> statement.setInt(1, id) },
            resultMapper = { rs ->
                rs.next()
                rs.getString("visibility") to rs.getInt("created_by")
            }
        ).process(ideaId)

        when (result) {
            is Result.Failure -> call.defaultHandleError(result.error)
            is Result.Success -> {
                val (visibility, createdBy) = result.value
                if (!IdeaVisibility.valueOf(visibility).isVisibleTo(user, createdBy)) {
                    call.defaultHandleError(AppFailure.DatabaseError.NotFound)
                }
            }
        }
    }
}
