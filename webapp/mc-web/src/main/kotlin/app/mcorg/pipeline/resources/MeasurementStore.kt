package app.mcorg.pipeline.resources

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import java.time.Instant

/**
 * What the tagged chests say about one item (MCO-539).
 *
 * Evidence, never progress. `resource_gathering_progress.collected` is the human's number and no
 * sweep touches it; this is the reporter's, and the two are shown side by side rather than
 * reconciled. The whole feature turns on that: you type 500, tag your first chest, and the sweep
 * says 12 — a design where the measurement wins has just destroyed a correct number with an
 * incomplete one.
 */
data class MeasuredStock(
    val measured: Long,
    val containerCount: Int,
    val oldestSeenAt: Instant?,
)

/**
 * This project's measurements, by item id.
 *
 * Reads the materialised rollup, so it is one indexed query — the project page is already
 * expensive and this must not be the thing that makes it worse.
 */
object GetProjectMeasurementsStep : Step<Int, AppFailure.DatabaseError, Map<String, MeasuredStock>> {
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
 * **The only path by which evidence becomes progress.** A sweep never does this on its own —
 * doing it automatically once the chests are trusted is MCO-540's, kept separate so this issue is
 * shippable and safe by itself.
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
                st.setString(2, i.itemId)
                st.setString(3, i.itemId)
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
                st.setNull(2, java.sql.Types.VARCHAR)
                st.setNull(3, java.sql.Types.VARCHAR)
            },
        ).process(input)
}

/**
 * Shared by both adopts, so a fix to the clamp or the stamp cannot land in one and miss the other.
 *
 * `LEAST(measured, required)` matches every other write to `collected`; where there is no
 * `resource_gathering` row — a plan item rather than a target — there is no ceiling to clamp to,
 * and the measurement stands as it is.
 */
private val ADOPT_SQL = SafeSQL.insert(
    """
    INSERT INTO resource_gathering_progress (project_id, item_id, collected, updated_at, progress_source)
    SELECT m.project_id,
           m.item_id,
           LEAST(m.measured, COALESCE(rg.required::bigint, m.measured))::int,
           CURRENT_TIMESTAMP,
           'mod'
    FROM resource_gathering_measurement m
    LEFT JOIN resource_gathering rg
           ON rg.project_id = m.project_id AND rg.item_id = m.item_id
    WHERE m.project_id = ?
      AND (CAST(? AS text) IS NULL OR m.item_id = CAST(? AS text))
    ON CONFLICT (project_id, item_id) DO UPDATE
        SET collected       = EXCLUDED.collected,
            updated_at      = CURRENT_TIMESTAMP,
            progress_source = 'mod'
    """.trimIndent()
)
