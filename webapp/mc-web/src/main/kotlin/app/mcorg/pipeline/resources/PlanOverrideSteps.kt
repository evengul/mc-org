package app.mcorg.pipeline.resources

import app.mcorg.domain.pipeline.Step
import app.mcorg.engine.plan.PlanOverrides
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.TransactionConnection
import app.mcorg.pipeline.failure.AppFailure

/**
 * A single user-pinned planning choice for one item of a project.
 * The full plan is never persisted — the engine re-derives it from the graph,
 * the targets, and these overrides.
 *
 * [plannerPick] is the MCO-506 half: what the planner would have chosen for this item at the
 * moment the user answered. With it a row is a *diff* ("the planner said cobblestone, the user
 * said cobbled deepslate") rather than a bare answer, and agreement, correction and
 * "the planner had no opinion" become distinguishable after the fact. Null means unknown, never
 * "agreed" — the rows written before MCO-506 carry null and cannot be backfilled.
 */
sealed interface PlanOverride {
    val itemId: String

    /** What the planner had selected for [itemId] when this choice was made; null when unknown. */
    val plannerPick: String?

    /** The user's answer, in the same vocabulary as [plannerPick], for diffing the two. */
    val chosen: String

    /** Pin a specific source for the item. [plannerPick] is the source key the planner had chosen. */
    data class Source(
        override val itemId: String,
        val sourceKey: String,
        override val plannerPick: String? = null,
    ) : PlanOverride {
        override val chosen: String get() = sourceKey
    }

    /**
     * Choose the concrete member item for a tag ("any planks" -> oak_planks).
     *
     * [plannerPick] here is the *recommendation* rather than a selection: an open tag is by
     * definition the case where the planner declined to choose, so the thing worth recording is
     * the member the picker's ranking put first — the answer the user would have got for free.
     * That makes the bulk "answer the remaining N" action (MCO-507) indistinguishable in the
     * data from a user who clicked each recommendation by hand, which is exactly the intent.
     */
    data class TagMember(
        override val itemId: String,
        val memberItemId: String,
        override val plannerPick: String? = null,
    ) : PlanOverride {
        override val chosen: String get() = memberItemId
    }
}

/**
 * Loads all of a project's **live** overrides as engine-ready [PlanOverrides].
 *
 * `superseded_at IS NULL` is the whole of the history filter, and the partial unique index
 * `unique_live_override_per_project_item` is what makes it safe: the database refuses a second
 * live row per (project_id, item_id), so this still returns exactly one answer per question no
 * matter how deep the history behind it gets.
 *
 * Deliberately not stored anywhere, and please do not add it (MCO-506): the picks the planner
 * made that the user *left alone*. The plan is re-derived from the graph on every read, so those
 * are computable on demand from [GenerateGatheringPlanStep]'s output; persisting them would mean
 * a write per node per plan render for data that is already free.
 */
object GetPlanOverridesStep : Step<Int, AppFailure.DatabaseError, PlanOverrides> {

    private val query = DatabaseSteps.query<Int, PlanOverrides>(
        sql = SafeSQL.select(
            """
            SELECT item_id, source_key, tag_member
            FROM resource_gathering_plan_override
            WHERE project_id = ? AND superseded_at IS NULL
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

/**
 * Records one answer for one item of a project, and returns the id of the live row.
 *
 * History, not replacement (MCO-506). A re-answer stamps `superseded_at` on the previous live row
 * and inserts a new one, because the second answer to a question is a stronger signal than the
 * first and the old behaviour — `ON CONFLICT ... DO UPDATE` — destroyed it. Both statements run
 * in one transaction so the partial unique index never sees two live rows.
 *
 * Re-submitting the answer that is already live is a no-op that only touches `updated_at`. That
 * is not a re-answer: clicking the already-selected option in the picker is how the UI reads back
 * the current state, and counting it would inflate the "changed their mind" signal the history
 * exists to measure. The originally recorded `planner_pick` is kept for the same reason — it
 * belongs to the moment the disagreement happened.
 */
class UpsertPlanOverrideStep(
    private val projectId: Int
) : Step<PlanOverride, AppFailure.DatabaseError, Int> {

    private data class LiveRow(val id: Int, val chosen: String?)

    override suspend fun process(input: PlanOverride): Result<AppFailure.DatabaseError, Int> {
        val step = DatabaseSteps.transaction<PlanOverride, Int> { tx ->
            object : Step<PlanOverride, AppFailure.DatabaseError, Int> {
                override suspend fun process(choice: PlanOverride): Result<AppFailure.DatabaseError, Int> {
                    val live = when (val r = selectLive(tx).process(choice)) {
                        is Result.Success -> r.value
                        is Result.Failure -> return r
                    }

                    if (live != null && live.chosen == choice.chosen) {
                        return when (val r = touch(tx).process(live.id)) {
                            is Result.Success -> Result.success(live.id)
                            is Result.Failure -> r
                        }
                    }

                    if (live != null) {
                        when (val r = supersede(tx).process(live.id)) {
                            is Result.Success -> Unit
                            is Result.Failure -> return r
                        }
                    }
                    return insert(tx).process(choice)
                }
            }
        }
        return step.process(input)
    }

    private fun selectLive(tx: TransactionConnection) =
        DatabaseSteps.query<PlanOverride, LiveRow?>(
            sql = SafeSQL.select(
                """
                SELECT id, source_key, tag_member
                FROM resource_gathering_plan_override
                WHERE project_id = ? AND item_id = ? AND superseded_at IS NULL
                """.trimIndent()
            ),
            parameterSetter = { ps, choice ->
                ps.setInt(1, projectId)
                ps.setString(2, choice.itemId)
            },
            resultMapper = { rs ->
                if (rs.next()) LiveRow(rs.getInt("id"), rs.getString("tag_member") ?: rs.getString("source_key"))
                else null
            },
            transactionConnection = tx,
        )

    private fun touch(tx: TransactionConnection) = DatabaseSteps.update<Int>(
        sql = SafeSQL.update(
            "UPDATE resource_gathering_plan_override SET updated_at = CURRENT_TIMESTAMP WHERE id = ?"
        ),
        parameterSetter = { ps, id -> ps.setInt(1, id) },
        transactionConnection = tx,
    )

    private fun supersede(tx: TransactionConnection) = DatabaseSteps.update<Int>(
        sql = SafeSQL.update(
            """
            UPDATE resource_gathering_plan_override
            SET superseded_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND superseded_at IS NULL
            """.trimIndent()
        ),
        parameterSetter = { ps, id -> ps.setInt(1, id) },
        transactionConnection = tx,
    )

    private fun insert(tx: TransactionConnection) = DatabaseSteps.update<PlanOverride>(
        sql = SafeSQL.insert(
            """
            INSERT INTO resource_gathering_plan_override (project_id, item_id, source_key, tag_member, planner_pick)
            VALUES (?, ?, ?, ?, ?)
            RETURNING id
            """.trimIndent()
        ),
        parameterSetter = { ps, choice ->
            ps.setInt(1, projectId)
            ps.setString(2, choice.itemId)
            when (choice) {
                is PlanOverride.Source -> {
                    ps.setString(3, choice.sourceKey)
                    ps.setNull(4, java.sql.Types.VARCHAR)
                }
                is PlanOverride.TagMember -> {
                    ps.setNull(3, java.sql.Types.VARCHAR)
                    ps.setString(4, choice.memberItemId)
                }
            }
            val plannerPick = choice.plannerPick
            if (plannerPick == null) ps.setNull(5, java.sql.Types.VARCHAR) else ps.setString(5, plannerPick)
        },
        transactionConnection = tx,
    )
}

/**
 * Removes the live override for one item of a project; the planner falls back to scorer defaults.
 *
 * Only the live row goes. Superseded rows are history — the record of a decision that was
 * actually made — and withdrawing today's answer is not a reason to forget the ones before it.
 */
class ClearPlanOverrideStep(
    private val projectId: Int
) : Step<String, AppFailure.DatabaseError, Int> {

    override suspend fun process(input: String): Result<AppFailure.DatabaseError, Int> {
        val step = DatabaseSteps.update<String>(
            sql = SafeSQL.delete(
                "DELETE FROM resource_gathering_plan_override WHERE project_id = ? AND item_id = ? AND superseded_at IS NULL"
            ),
            parameterSetter = { ps, itemId ->
                ps.setInt(1, projectId)
                ps.setString(2, itemId)
            }
        )
        return step.process(input)
    }
}

/**
 * Removes specific override rows by primary key — the undo half of MCO-507's bulk answer.
 *
 * By id rather than by item id, because "revert those N" has to remove *exactly* what the one
 * action created and nothing a user answered themselves. Row identity is the only thing that can
 * promise that: two answers to the same question are two different rows.
 *
 * Scoped to [projectId] as well so an id from another project cannot be deleted through it, and
 * to live rows so an undo can never rewrite history. Nothing needs un-superseding on the way
 * back: the bulk action only ever answers questions that are still open, so its rows never
 * superseded anything.
 *
 * @return the number of rows actually removed.
 */
class DeletePlanOverridesByIdStep(
    private val projectId: Int
) : Step<List<Int>, AppFailure.DatabaseError, Int> {

    override suspend fun process(input: List<Int>): Result<AppFailure.DatabaseError, Int> {
        if (input.isEmpty()) return Result.success(0)
        val step = DatabaseSteps.update<List<Int>>(
            sql = SafeSQL.delete(
                """
                DELETE FROM resource_gathering_plan_override
                WHERE project_id = ? AND superseded_at IS NULL AND id = ANY (?)
                """.trimIndent()
            ),
            parameterSetter = { ps, ids ->
                ps.setInt(1, projectId)
                ps.setArray(2, ps.connection.createArrayOf("integer", ids.toTypedArray()))
            }
        )
        return step.process(input)
    }
}
