package app.mcorg.pipeline.idea.draft

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.idea.IdeaDraft
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.idea.CreateIdeaStep

data class PublishDraftInput(val draft: IdeaDraft, val userId: Int)

/**
 * Publishes a draft as a full idea.
 *
 * For new ideas: DeserializeDraftStep → CreateIdeaStep → CacheManager → DeleteDraftStep
 * For edits (sourceIdeaId set): DeserializeDraftStep → UpdateExistingIdeaStep → CacheManager → DeleteDraftStep
 */
class PublishDraftStep : Step<PublishDraftInput, AppFailure, Int> {
    override suspend fun process(input: PublishDraftInput): Result<AppFailure, Int> {
        val createInput = when (val r = DeserializeDraftStep.process(input.draft)) {
            is Result.Failure -> return Result.failure(r.error)
            is Result.Success -> r.value
        }

        val ideaId = when (val sourceId = input.draft.sourceIdeaId) {
            null -> {
                when (val r = CreateIdeaStep(input.userId).process(createInput)) {
                    is Result.Failure -> return Result.failure(r.error)
                    is Result.Success -> r.value
                }
            }
            else -> {
                when (val r = UpdateExistingIdeaStep().process(UpdateExistingIdeaInput(sourceId, createInput))) {
                    is Result.Failure -> return Result.failure(r.error)
                    is Result.Success -> r.value
                }
            }
        }

        CacheManager.onIdeaCreated(ideaId)

        when (val r = DeleteDraftStep().process(DeleteDraftInput(input.draft.id, input.userId))) {
            is Result.Failure -> return Result.failure(r.error)
            is Result.Success -> { /* draft deleted */ }
        }

        return Result.success(ideaId)
    }
}
