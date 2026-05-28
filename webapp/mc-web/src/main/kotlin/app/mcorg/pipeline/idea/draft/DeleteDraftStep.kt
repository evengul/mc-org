package app.mcorg.pipeline.idea.draft

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

data class DeleteDraftInput(val draftId: Int, val userId: Int)

class DeleteDraftStep : Step<DeleteDraftInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: DeleteDraftInput): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<DeleteDraftInput>(
            sql = SafeSQL.delete(
                "DELETE FROM idea_drafts WHERE id = ? AND user_id = ?"
            ),
            parameterSetter = { stmt, inp ->
                stmt.setInt(1, inp.draftId)
                stmt.setInt(2, inp.userId)
            }
        ).process(input)
    }
}
