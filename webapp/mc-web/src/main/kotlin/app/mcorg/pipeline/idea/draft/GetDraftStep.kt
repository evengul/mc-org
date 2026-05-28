package app.mcorg.pipeline.idea.draft

import app.mcorg.domain.model.idea.IdeaDraft
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

data class GetDraftInput(val draftId: Int, val userId: Int)

class GetDraftStep : Step<GetDraftInput, AppFailure.DatabaseError, IdeaDraft> {
    override suspend fun process(input: GetDraftInput): Result<AppFailure.DatabaseError, IdeaDraft> {
        val queryStep = DatabaseSteps.query<GetDraftInput, IdeaDraft?>(
            sql = SafeSQL.select(
                """
                SELECT id, user_id, data::text, current_stage, source_idea_id, created_at, updated_at
                FROM idea_drafts
                WHERE id = ? AND user_id = ?
                """.trimIndent()
            ),
            parameterSetter = { stmt, inp ->
                stmt.setInt(1, inp.draftId)
                stmt.setInt(2, inp.userId)
            },
            resultMapper = { rs -> if (rs.next()) rs.mapToIdeaDraft() else null }
        )

        return queryStep.process(input).flatMap { draft ->
            if (draft != null) Result.success(draft)
            else Result.failure(AppFailure.DatabaseError.NotFound)
        }
    }
}
