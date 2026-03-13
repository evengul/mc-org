package app.mcorg.pipeline.idea.draft

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

class CreateDraftStep(
    private val userId: Int,
    private val sourceIdeaId: Int? = null,
    private val initialData: String = "{}"
) : Step<Unit, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, Int> {
        val initialStage = "BASIC_INFO"
        return DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                """
                INSERT INTO idea_drafts (user_id, data, current_stage, source_idea_id)
                VALUES (?, ?::jsonb, ?, ?)
                RETURNING id
                """.trimIndent()
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, userId)
                stmt.setString(2, initialData)
                stmt.setString(3, initialStage)
                if (sourceIdeaId != null) stmt.setInt(4, sourceIdeaId) else stmt.setNull(4, java.sql.Types.INTEGER)
            }
        ).process(input)
    }
}
