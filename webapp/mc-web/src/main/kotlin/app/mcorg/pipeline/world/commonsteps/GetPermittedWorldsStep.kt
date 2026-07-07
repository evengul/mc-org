package app.mcorg.pipeline.world.commonsteps

import app.mcorg.domain.model.world.World
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.world.extractors.toWorld

data class GetPermittedWorldsInput(
    val userId: Int,
    val query: String = "",
    val sortBy: String = "lastModified_desc"
)

object GetPermittedWorldsStep : Step<GetPermittedWorldsInput, AppFailure.DatabaseError, List<World>> {
    override suspend fun process(input: GetPermittedWorldsInput): Result<AppFailure.DatabaseError, List<World>> {
        // Redesigned Worlds page ordering: a user's pinned worlds first, then the
        // most-recently-opened. `name_asc` still respects the pin, for the search API.
        val sortByValue = when(input.sortBy) {
            "name_asc" -> "wm.pinned DESC, w.name ASC"
            else -> "wm.pinned DESC, wm.last_opened_at DESC NULLS LAST, w.updated_at DESC, w.name ASC"
        }

        return DatabaseSteps.query<GetPermittedWorldsInput, List<World>>(
            sql = SafeSQL.select("""
                SELECT
                    w.id,
                    w.name,
                    w.description,
                    w.version,
                    w.created_at,
                    w.updated_at,
                    wm.pinned,
                    wm.last_opened_at,
                    COALESCE(COUNT(DISTINCT p.id), 0) as total_projects,
                    COALESCE(COUNT(DISTINCT CASE WHEN p.stage = 'COMPLETED' THEN p.id END), 0) as completed_projects
                FROM world w
                INNER JOIN world_members wm ON w.id = wm.world_id
                LEFT JOIN projects p ON w.id = p.world_id
                WHERE wm.user_id = ? AND (? = '' OR LOWER(w.name) ILIKE '%' || ? || '%' OR LOWER(w.description) ILIKE '%' || ? || '%')
                GROUP BY w.id, w.name, w.description, w.version, w.created_at, w.updated_at, wm.pinned, wm.last_opened_at
                ORDER BY $sortByValue
            """.trimIndent()),
            parameterSetter = { statement, inputData ->
                statement.setInt(1, inputData.userId)

                val normalizedSearch = input.query.trim().lowercase()
                statement.setString(2, normalizedSearch)
                statement.setString(3, normalizedSearch)
                statement.setString(4, normalizedSearch)
            },
            resultMapper = { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            resultSet.toWorld().copy(
                                pinned = resultSet.getBoolean("pinned"),
                                lastOpenedAt = resultSet.getTimestamp("last_opened_at")
                                    ?.toInstant()?.atZone(java.time.ZoneOffset.UTC)
                            )
                        )
                    }
                }
            }
        ).process(input)
    }
}
