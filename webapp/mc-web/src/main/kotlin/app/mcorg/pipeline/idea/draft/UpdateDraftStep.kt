package app.mcorg.pipeline.idea.draft

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

data class UpdateDraftInput(
    val draftId: Int,
    val userId: Int,
    val stageDataJson: String,
    val currentStage: String
)

class UpdateDraftStep : Step<UpdateDraftInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: UpdateDraftInput): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<UpdateDraftInput>(
            sql = SafeSQL.update(
                """
                UPDATE idea_drafts
                SET data = data || ?::jsonb, current_stage = ?, updated_at = now()
                WHERE id = ? AND user_id = ?
                """.trimIndent()
            ),
            parameterSetter = { stmt, inp ->
                stmt.setString(1, inp.stageDataJson)
                stmt.setString(2, inp.currentStage)
                stmt.setInt(3, inp.draftId)
                stmt.setInt(4, inp.userId)
            }
        ).process(input)
    }
}
