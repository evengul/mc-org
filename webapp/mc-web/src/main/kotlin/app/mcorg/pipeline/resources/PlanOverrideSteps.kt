package app.mcorg.pipeline.resources

import app.mcorg.domain.pipeline.Step
import app.mcorg.engine.plan.PlanOverrides
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/**
 * A single user-pinned planning choice for one item of a project.
 * The full plan is never persisted — the engine re-derives it from the graph,
 * the targets, and these overrides.
 */
sealed interface PlanOverride {
    val itemId: String

    /** Pin a specific source for the item. */
    data class Source(override val itemId: String, val sourceKey: String) : PlanOverride

    /** Choose the concrete member item for a tag ("any planks" -> oak_planks). */
    data class TagMember(override val itemId: String, val memberItemId: String) : PlanOverride
}

/** Loads all of a project's overrides as engine-ready [PlanOverrides]. */
object GetPlanOverridesStep : Step<Int, AppFailure.DatabaseError, PlanOverrides> {

    private val query = DatabaseSteps.query<Int, PlanOverrides>(
        sql = SafeSQL.select(
            """
            SELECT item_id, source_key, tag_member
            FROM resource_gathering_plan_override
            WHERE project_id = ?
            """.trimIndent()
        ),
        parameterSetter = { ps, projectId -> ps.setInt(1, projectId) },
        resultMapper = { rs ->
            val sourceByItem = mutableMapOf<String, String>()
            val tagMember = mutableMapOf<String, String>()
            while (rs.next()) {
                val itemId = rs.getString("item_id")
                rs.getString("source_key")?.let { sourceByItem[itemId] = it }
                rs.getString("tag_member")?.let { tagMember[itemId] = it }
            }
            PlanOverrides(sourceByItem = sourceByItem, tagMember = tagMember)
        }
    )

    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, PlanOverrides> =
        query.process(input)
}

/** Inserts or replaces the override for one item of a project. */
class UpsertPlanOverrideStep(
    private val projectId: Int
) : Step<PlanOverride, AppFailure.DatabaseError, Int> {

    override suspend fun process(input: PlanOverride): Result<AppFailure.DatabaseError, Int> {
        val step = DatabaseSteps.update<PlanOverride>(
            sql = SafeSQL.insert(
                """
                INSERT INTO resource_gathering_plan_override (project_id, item_id, source_key, tag_member)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (project_id, item_id)
                DO UPDATE SET source_key = EXCLUDED.source_key,
                              tag_member = EXCLUDED.tag_member,
                              updated_at = CURRENT_TIMESTAMP
                RETURNING id
                """.trimIndent()
            ),
            parameterSetter = { ps, override ->
                ps.setInt(1, projectId)
                ps.setString(2, override.itemId)
                when (override) {
                    is PlanOverride.Source -> {
                        ps.setString(3, override.sourceKey)
                        ps.setNull(4, java.sql.Types.VARCHAR)
                    }
                    is PlanOverride.TagMember -> {
                        ps.setNull(3, java.sql.Types.VARCHAR)
                        ps.setString(4, override.memberItemId)
                    }
                }
            }
        )
        return step.process(input)
    }
}

/** Removes the override for one item of a project; the planner falls back to scorer defaults. */
class ClearPlanOverrideStep(
    private val projectId: Int
) : Step<String, AppFailure.DatabaseError, Int> {

    override suspend fun process(input: String): Result<AppFailure.DatabaseError, Int> {
        val step = DatabaseSteps.update<String>(
            sql = SafeSQL.delete(
                "DELETE FROM resource_gathering_plan_override WHERE project_id = ? AND item_id = ?"
            ),
            parameterSetter = { ps, itemId ->
                ps.setInt(1, projectId)
                ps.setString(2, itemId)
            }
        )
        return step.process(input)
    }
}
