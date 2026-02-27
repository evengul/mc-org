package app.mcorg.pipeline.idea.single

import app.mcorg.config.CacheManager
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getIdeaId
import app.mcorg.presentation.utils.getUser
import io.ktor.server.application.*

suspend fun ApplicationCall.handleDeleteIdea() {
    val ideaId = this.getIdeaId()
    val userId = this.getUser().id

    handlePipeline(
        onSuccess = { clientRedirect(Link.Ideas.to) }
    ) {
        ValidateIdeaOwnerStep(userId).run(ideaId)
        DeleteIdeaStep.run(ideaId)
        CacheManager.onIdeaDeleted(ideaId)
    }
}

private data class ValidateIdeaOwnerStep(val userId: Int) : Step<Int, AppFailure, Unit> {
    override suspend fun process(input: Int): Result<AppFailure, Unit> {
        return DatabaseSteps.query<Int, Boolean>(
            sql = SafeSQL.select("SELECT COUNT(*) > 0 AS is_owner FROM ideas WHERE id = ? AND created_by = ?"),
            parameterSetter = { statement, ideaId ->
                statement.setInt(1, ideaId)
                statement.setInt(2, userId)
            },
            resultMapper = { resultSet ->
                resultSet.next() && resultSet.getBoolean("is_owner")
            }
        ).process(input)
            .map {
                if (it) {
                    Result.success()
                } else {
                    Result.failure(AppFailure.AuthError.NotAuthorized)
                }
            }
    }
}

private val DeleteIdeaStep = DatabaseSteps.update<Int>(
    sql = SafeSQL.delete("DELETE FROM ideas WHERE id = ?"),
    parameterSetter = { ps, input -> ps.setInt(1, input) }
)
