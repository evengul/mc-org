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
)

object GetPermittedWorldsStep : Step<GetPermittedWorldsInput, AppFailure.DatabaseError, List<World>> {
    override suspend fun process(input: GetPermittedWorldsInput): Result<AppFailure.DatabaseError, List<World>> {
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
                    w.farm_scale_threshold,
                    w.preferred_wood_species,
                    COALESCE(COUNT(DISTINCT p.id), 0) as total_projects,
                    COALESCE(COUNT(DISTINCT CASE WHEN p.stage = 'COMPLETED' THEN p.id END), 0) as completed_projects,
                    ${projectTallyColumns("p")}
                FROM world w
                INNER JOIN world_members wm ON w.id = wm.world_id
                LEFT JOIN projects p ON w.id = p.world_id
                WHERE wm.user_id = ?
                GROUP BY w.id, w.name, w.description, w.version, w.created_at, w.updated_at, wm.pinned, wm.last_opened_at, w.farm_scale_threshold, w.preferred_wood_species
                -- Worlds page ordering: a user's pinned worlds first, then the most-recently-opened.
                --
                -- `w.id DESC` is a tiebreaker, not a preference (MCO-500). Every column before it
                -- can tie: `pinned` is a boolean, `last_opened_at` is NULL for every world the user
                -- has not opened yet, `updated_at` defaults to CURRENT_TIMESTAMP — which is the
                -- *transaction* timestamp, identical for worlds created in one transaction — and
                -- `name` has no unique constraint, only an index, so two worlds may share it. Two
                -- never-opened worlds with the same name therefore tied on every key, and which one
                -- the page put in the hero slot was down to whatever order the plan happened to
                -- return. Ordering on the primary key last makes it total, and picks the newer
                -- world, which matches the recency the keys before it are reaching for.
                ORDER BY wm.pinned DESC, wm.last_opened_at DESC NULLS LAST, w.updated_at DESC, w.name ASC, w.id DESC
            """.trimIndent()),
            parameterSetter = { statement, inputData ->
                statement.setInt(1, inputData.userId)
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
