package app.mcorg.pipeline.idea.draft

import app.mcorg.domain.model.idea.IdeaDraft
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import java.sql.ResultSet

class GetDraftsStep(
    private val userId: Int
) : Step<Unit, AppFailure.DatabaseError, List<IdeaDraft>> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<IdeaDraft>> {
        return DatabaseSteps.query<Unit, List<IdeaDraft>>(
            sql = SafeSQL.select(
                """
                SELECT id, user_id, data::text, current_stage, source_idea_id, created_at, updated_at
                FROM idea_drafts
                WHERE user_id = ?
                ORDER BY updated_at DESC
                """.trimIndent()
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, userId) },
            resultMapper = { rs ->
                val list = mutableListOf<IdeaDraft>()
                while (rs.next()) list.add(rs.mapToIdeaDraft())
                list
            }
        ).process(input)
    }
}

internal fun ResultSet.mapToIdeaDraft(): IdeaDraft = IdeaDraft(
    id = getInt("id"),
    userId = getInt("user_id"),
    data = getString("data") ?: "{}",
    currentStage = getString("current_stage"),
    sourceIdeaId = getInt("source_idea_id").takeIf { !wasNull() },
    createdAt = getTimestamp("created_at").toInstant().atZone(java.time.ZoneOffset.UTC),
    updatedAt = getTimestamp("updated_at").toInstant().atZone(java.time.ZoneOffset.UTC)
)
