package app.mcorg.pipeline.idea.single

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getIdeaId
import app.mcorg.presentation.utils.getUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

sealed interface DeleteIdeaFailure {
    object NotOwner : DeleteIdeaFailure
    object DatabaseError : DeleteIdeaFailure
}

suspend fun ApplicationCall.handleDeleteIdea() {
    val ideaId = this.getIdeaId()
    val userId = this.getUser().id

    executePipeline(
        onSuccess = { clientRedirect(Link.Ideas.to) },
        onFailure = { respond(HttpStatusCode.InternalServerError, "Failed to delete idea") }
    ) {
        step(Step.value(ValidateIdeaOwnerInput(ideaId, userId)))
            .step(object : Step<ValidateIdeaOwnerInput, DeleteIdeaFailure, Unit> {
                override suspend fun process(input: ValidateIdeaOwnerInput): Result<DeleteIdeaFailure, Unit> {
                    return ValidateIdeaOwnerStep.process(input)
                        .mapError {
                            when (it) {
                                ValidateIdeaOwnerFailure.NotOwner -> DeleteIdeaFailure.NotOwner
                                ValidateIdeaOwnerFailure.DatabaseError -> DeleteIdeaFailure.DatabaseError
                            }
                        }
                }
            })
            .step(Step.value(ideaId))
            .step(DeleteIdeaStep)
    }
}

private object DeleteIdeaStep : Step<Int, DeleteIdeaFailure, Unit> {
    override suspend fun process(input: Int): Result<DeleteIdeaFailure, Unit> {
        return DatabaseSteps.update<Int, DeleteIdeaFailure.DatabaseError>(
            sql = SafeSQL.delete("DELETE FROM ideas WHERE id = ?"),
            parameterSetter = { ps, input -> ps.setInt(1, input) },
            errorMapper = { DeleteIdeaFailure.DatabaseError }
        ).process(input).map {  }
    }
}