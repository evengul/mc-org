package app.mcorg.pipeline.resources

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.TransactionConnection
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.getOrElse
import java.time.Instant

/**
 * What the tagged chests say about one item (MCO-539).
 *
 * Evidence, not progress. `resource_gathering_progress.collected` is the human's number and a sweep
 * leaves it alone; this is the reporter's, and the two are shown side by side rather than
 * reconciled. The whole feature turns on that: you type 500, tag your first chest, and the sweep
 * says 12 — a design where the measurement wins has just destroyed a correct number with an
 * incomplete one. The one exception is a project someone has switched to storage-tracked
 * (MCO-540), and that is a decision a person makes, not one the sweep does.
 */
data class MeasuredStock(
    val measured: Long,
    val containerCount: Int,
    val oldestSeenAt: Instant?,
)

/**
 * What a project's tagged chests say, by item id, and whether its counts follow them.
 *
 * One value rather than a map and a flag passed side by side, because every surface that draws a
 * counter already receives the measurements, and every one of them has to know whether the counter
 * may be typed into. A flag passed separately is a flag some surface forgets.
 */
class ProjectMeasurements(
    private val byItem: Map<String, MeasuredStock>,
    /** Non-null when the project is storage-tracked: its counts are the chests', not typed. */
    val followed: FollowedChests? = null,
) : Map<String, MeasuredStock> by byItem {
    companion object {
        val NONE = ProjectMeasurements(emptyMap())
    }
}

/**
 * A storage-tracked project's chests, for the line that says where a read-only count comes from.
 *
 * [containerCount] counts readable containers only. An item that is in none of them reads 0, and
 * the row says "in none of the 4 tagged chests" rather than leaving a bare zero to explain itself.
 */
data class FollowedChests(val containerCount: Int)

/**
 * A project's storage-tracked setting, with what the settings page needs to say about it.
 *
 * [taggedContainers] counts every tag, whatever its state; [readableContainers] only those the last
 * sweep could read, which are the ones a followed count is made of.
 */
data class StorageTracking(
    val tracked: Boolean,
    val taggedContainers: Int,
    val readableContainers: Int,
)

object GetStorageTrackingStep : Step<Int, AppFailure.DatabaseError, StorageTracking> {
    override suspend fun process(input: Int) =
        DatabaseSteps.query<Int, StorageTracking>(
            sql = SafeSQL.select(
                """
                SELECT p.storage_tracked,
                       COUNT(ct.id) AS tagged,
                       COUNT(ct.id) FILTER (WHERE ct.state = 'ok') AS readable
                FROM projects p
                LEFT JOIN container_tags ct ON ct.project_id = p.id
                WHERE p.id = ?
                GROUP BY p.storage_tracked
                """.trimIndent()
            ),
            parameterSetter = { st, projectId -> st.setInt(1, projectId) },
            resultMapper = { rs ->
                if (rs.next()) {
                    StorageTracking(
                        tracked = rs.getBoolean("storage_tracked"),
                        taggedContainers = rs.getInt("tagged"),
                        readableContainers = rs.getInt("readable"),
                    )
                } else {
                    StorageTracking(tracked = false, taggedContainers = 0, readableContainers = 0)
                }
            },
        ).process(input)
}

/**
 * This project's measurements, by item id, and whether its counts follow them.
 *
 * Reads the materialised rollup, so it is one indexed query plus one for the setting — the project
 * page is already expensive and this must not be the thing that makes it worse.
 */
object GetProjectMeasurementsStep : Step<Int, AppFailure.DatabaseError, ProjectMeasurements> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, ProjectMeasurements> {
        val byItem = GetMeasurementsByItemStep.process(input).getOrElse { return Result.failure(it) }
        val tracking = GetStorageTrackingStep.process(input).getOrElse { return Result.failure(it) }
        return Result.success(
            ProjectMeasurements(
                byItem = byItem,
                followed = if (tracking.tracked) FollowedChests(tracking.readableContainers) else null,
            )
        )
    }
}

private object GetMeasurementsByItemStep : Step<Int, AppFailure.DatabaseError, Map<String, MeasuredStock>> {
    override suspend fun process(input: Int) =
        DatabaseSteps.query<Int, Map<String, MeasuredStock>>(
            sql = SafeSQL.select(
                """
                SELECT item_id, measured, container_count, oldest_seen_at
                FROM resource_gathering_measurement
                WHERE project_id = ?
                """.trimIndent()
            ),
            parameterSetter = { st, projectId -> st.setInt(1, projectId) },
            resultMapper = { rs ->
                buildMap {
                    while (rs.next()) {
                        put(
                            rs.getString("item_id"),
                            MeasuredStock(
                                measured = rs.getLong("measured"),
                                containerCount = rs.getInt("container_count"),
                                oldestSeenAt = rs.getTimestamp("oldest_seen_at")?.toInstant(),
                            ),
                        )
                    }
                }
            },
        ).process(input)
}

data class AdoptMeasurementInput(val projectId: Int, val itemId: String)

/**
 * Adopt one measurement: `collected := measured`, stamped as the mod's.
 *
 * **The only path by which evidence becomes progress** on a project that is not storage-tracked. A
 * sweep never does this on its own; following the chests automatically, once someone trusts them,
 * is [FollowMeasurementStep]'s (MCO-540).
 *
 * Capped at `required` the way every other progress write is, and driven off the measurement row
 * rather than a number from the browser: a client that could post its own `collected` here would
 * make "adopt" a general-purpose write dressed as a one-click convenience.
 */
object AdoptMeasurementStep : Step<AdoptMeasurementInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: AdoptMeasurementInput) =
        DatabaseSteps.update<AdoptMeasurementInput>(
            sql = ADOPT_SQL,
            parameterSetter = { st, i ->
                st.setInt(1, i.projectId)
                st.setInt(2, i.projectId)
                st.setString(3, i.itemId)
                st.setString(4, i.itemId)
            },
        ).process(input)
}

/**
 * Adopt every measurement on a project — "I have finished tagging, take the lot", which is the
 * realistic moment rather than clicking twelve chips.
 *
 * Returns the number of rows written, so the caller can say what it changed instead of claiming
 * success silently.
 */
object AdoptAllMeasurementsStep : Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int) =
        DatabaseSteps.update<Int>(
            sql = ADOPT_SQL,
            // A null item id means "every item on the project" — see the WHERE clause. One
            // statement rather than two so the clamp and the stamp cannot be fixed in one and
            // missed in the other, and because splicing a filter onto SQL is what
            // SafeSqlSourceScanTest exists to stop.
            parameterSetter = { st, projectId ->
                st.setInt(1, projectId)
                st.setInt(2, projectId)
                st.setNull(3, java.sql.Types.VARCHAR)
                st.setNull(4, java.sql.Types.VARCHAR)
            },
        ).process(input)
}

/**
 * Shared by both adopts, so a fix to the clamp or the stamp cannot land in one and miss the other.
 *
 * `LEAST(measured, required)` matches every other write to `collected`; where there is no
 * `resource_gathering` row — a plan item rather than a target — there is no ceiling to clamp to,
 * and the measurement stands as it is.
 *
 * Targets are summed per item: nothing makes an item unique within a project, and joining two
 * target rows for one item made the upsert write one progress row twice, which Postgres refuses.
 */
private val ADOPT_SQL = SafeSQL.insert(
    """
    INSERT INTO resource_gathering_progress (project_id, item_id, collected, updated_at, progress_source)
    SELECT m.project_id,
           m.item_id,
           LEAST(m.measured, COALESCE(rg.required, m.measured))::int,
           CURRENT_TIMESTAMP,
           'mod'
    FROM resource_gathering_measurement m
    LEFT JOIN (
        SELECT item_id, SUM(required)::bigint AS required
        FROM resource_gathering
        WHERE project_id = ?
        GROUP BY item_id
    ) rg ON rg.item_id = m.item_id
    WHERE m.project_id = ?
      AND (CAST(? AS text) IS NULL OR m.item_id = CAST(? AS text))
    ON CONFLICT (project_id, item_id) DO UPDATE
        SET collected       = EXCLUDED.collected,
            updated_at      = CURRENT_TIMESTAMP,
            progress_source = 'mod'
    """.trimIndent()
)

/**
 * A storage-tracked project's counts, rewritten from its chests (MCO-540).
 *
 * Runs inside every measurement recompute — every sweep, tag and untag — so the counts follow the
 * chests on exactly the cadence the measurement itself changes, and in the same transaction: a
 * reader never sees a new measurement beside an old count. Does nothing for a project that is not
 * tracked.
 *
 * Unlike an adopt, which writes only the items the chests hold, this writes every item the project
 * counts: an item in none of the tagged chests reads 0. "The counts follow the chests" means that,
 * and leaving the last number standing would let an emptied chest keep claiming what it held.
 *
 * A project whose last tag is removed is switched back off rather than zeroed. Nothing is left to
 * follow, and zeroing a whole project because someone untagged a chest is the destructive surprise
 * the off-by-default setting exists to avoid.
 */
object FollowMeasurementStep {
    fun within(tx: TransactionConnection): Step<Int, AppFailure.DatabaseError, Int> =
        object : Step<Int, AppFailure.DatabaseError, Int> {
            override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> {
                DatabaseSteps.update<Int>(
                    sql = SafeSQL.update(
                        """
                        UPDATE projects SET storage_tracked = false
                        WHERE id = ? AND storage_tracked
                          AND NOT EXISTS (SELECT 1 FROM container_tags WHERE project_id = ?)
                        """.trimIndent()
                    ),
                    parameterSetter = { st, projectId ->
                        st.setInt(1, projectId)
                        st.setInt(2, projectId)
                    },
                    transactionConnection = tx,
                ).process(input).getOrElse { return Result.failure(it) }

                return DatabaseSteps.update<Int>(
                    sql = FOLLOW_SQL,
                    parameterSetter = { st, projectId -> repeat(5) { st.setInt(it + 1, projectId) } },
                    transactionConnection = tx,
                ).process(input)
            }
        }
}

/**
 * Every item the project counts — its targets, what the chests hold, and anything already
 * counted — set to what the chests hold, clamped to `required` the way every write to `collected`
 * is.
 *
 * Targets are summed per item because `resource_gathering` does not make an item unique within a
 * project, and two rows for one item would otherwise make the upsert write one row twice.
 *
 * The conflict update skips a row that already holds the value. A sweep runs every 30 seconds and
 * mostly changes nothing, and an unconditional update would rewrite every row each time — and drop
 * the project's derived plan each time, through the trigger that watches these rows (MCO-578).
 */
private val FOLLOW_SQL = SafeSQL.insert(
    """
    INSERT INTO resource_gathering_progress (project_id, item_id, collected, updated_at, progress_source)
    SELECT p.id,
           i.item_id,
           LEAST(COALESCE(m.measured, 0), COALESCE(rg.required, COALESCE(m.measured, 0)))::int,
           CURRENT_TIMESTAMP,
           'mod'
    FROM projects p
    JOIN (
        SELECT item_id FROM resource_gathering WHERE project_id = ?
        UNION
        SELECT item_id FROM resource_gathering_measurement WHERE project_id = ?
        UNION
        SELECT item_id FROM resource_gathering_progress WHERE project_id = ?
    ) i ON true
    LEFT JOIN resource_gathering_measurement m ON m.project_id = p.id AND m.item_id = i.item_id
    LEFT JOIN (
        SELECT item_id, SUM(required)::bigint AS required
        FROM resource_gathering
        WHERE project_id = ?
        GROUP BY item_id
    ) rg ON rg.item_id = i.item_id
    WHERE p.id = ? AND p.storage_tracked
    ON CONFLICT (project_id, item_id) DO UPDATE
        SET collected       = EXCLUDED.collected,
            updated_at      = CURRENT_TIMESTAMP,
            progress_source = 'mod'
        WHERE resource_gathering_progress.collected IS DISTINCT FROM EXCLUDED.collected
           OR resource_gathering_progress.progress_source <> 'mod'
    """.trimIndent()
)

data class SetStorageTrackedInput(val projectId: Int, val tracked: Boolean)

/**
 * Switches a project's counts to follow its chests, or back to typed numbers.
 *
 * On: refused for a project with no tagged containers — there is nothing to follow, and following
 * nothing would zero every count. The counts are followed at once rather than at the next sweep,
 * so the page that flipped the switch already shows the chests' numbers.
 *
 * Off: only the flag changes. The last followed values stay as ordinary counts, which is what
 * makes turning it back off safe — it restores a number someone can type into, it does not lose one.
 */
object SetStorageTrackedStep : Step<SetStorageTrackedInput, AppFailure, StorageTracking> {
    override suspend fun process(input: SetStorageTrackedInput): Result<AppFailure, StorageTracking> {
        if (input.tracked) {
            val current = GetStorageTrackingStep.process(input.projectId).getOrElse { return Result.failure(it) }
            if (current.taggedContainers == 0) {
                return Result.failure(
                    AppFailure.ValidationError(
                        listOf(
                            ValidationFailure.CustomValidation(
                                "storageTracked",
                                "Tag at least one chest to this project in game first. With nothing to follow, every count would read 0.",
                            )
                        )
                    )
                )
            }
        }
        DatabaseSteps.transaction<SetStorageTrackedInput, Int> { tx ->
            object : Step<SetStorageTrackedInput, AppFailure.DatabaseError, Int> {
                override suspend fun process(input: SetStorageTrackedInput): Result<AppFailure.DatabaseError, Int> {
                    DatabaseSteps.update<SetStorageTrackedInput>(
                        sql = SafeSQL.update("UPDATE projects SET storage_tracked = ? WHERE id = ?"),
                        parameterSetter = { st, i ->
                            st.setBoolean(1, i.tracked)
                            st.setInt(2, i.projectId)
                        },
                        transactionConnection = tx,
                    ).process(input).getOrElse { return Result.failure(it) }
                    return FollowMeasurementStep.within(tx).process(input.projectId)
                }
            }
        }.process(input).getOrElse { return Result.failure(it) }
        return GetStorageTrackingStep.process(input.projectId)
    }
}
