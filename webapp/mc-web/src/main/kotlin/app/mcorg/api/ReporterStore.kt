package app.mcorg.api

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.TransactionConnection
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.getOrElse
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Container contents and the measurement rollup (MCO-532).
 *
 * The reporter is stateless: it pushes what it read and remembers nothing. Everything that has to
 * survive between sweeps lives here, because a container in an unloaded chunk still counts — its
 * last reading is correct data, not stale data, and something must hold it.
 */

// ── Reporter authentication ──────────────────────────────────────────────────

/** A live reporter token resolved to the single world it speaks for. */
data class ReporterAuth(val tokenId: Long, val worldId: Int)

/**
 * Resolves a live reporter token by hash. Deliberately a different table from `api_token`, so this
 * is the *only* way a reporter token authenticates anything — see `V2_68_0`'s argument.
 */
object LookupReporterTokenStep : Step<String, AppFailure.DatabaseError, ReporterAuth> {
    override suspend fun process(input: String): Result<AppFailure.DatabaseError, ReporterAuth> =
        DatabaseSteps.query<String, ReporterAuth?>(
            sql = SafeSQL.select(
                """
                SELECT id, world_id
                FROM reporter_token
                WHERE token_hash = ? AND revoked_at IS NULL
                """.trimIndent()
            ),
            parameterSetter = { st, hash -> st.setString(1, hash) },
            resultMapper = { if (it.next()) ReporterAuth(it.getLong("id"), it.getInt("world_id")) else null },
        ).process(input).flatMap {
            if (it == null) Result.failure(AppFailure.DatabaseError.NotFound) else Result.success(it)
        }
}

data class TouchReporterTokenInput(val tokenId: Long, val reporterVersion: String?)

/**
 * Stamps the reporter's last-seen and build on a successful push. This is why there is no separate
 * heartbeat endpoint: a reporter with nothing to report pushes an empty payload, and that is the
 * heartbeat. Best-effort — a failed stamp must not fail the push.
 */
object TouchReporterTokenStep : Step<TouchReporterTokenInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: TouchReporterTokenInput) =
        DatabaseSteps.update<TouchReporterTokenInput>(
            sql = SafeSQL.update(
                """
                UPDATE reporter_token
                SET last_used_at = CURRENT_TIMESTAMP,
                    reporter_version = COALESCE(?, reporter_version)
                WHERE id = ?
                """.trimIndent()
            ),
            parameterSetter = { st, i ->
                if (i.reporterVersion != null) st.setString(1, i.reporterVersion)
                else st.setNull(1, java.sql.Types.VARCHAR)
                st.setLong(2, i.tokenId)
            },
        ).process(input)
}

// ── items_of_interest ────────────────────────────────────────────────────────

/**
 * The items a world's reporter should bother reporting, per project: the union of each project's
 * **target** items (`resource_gathering`) and its **plan** items (`project_demand`).
 *
 * This bounds the payload and means the API never receives an inventory of somebody's junk drawer.
 * Reporting everything found would hand the webapp a whole-world item census — genuinely
 * interesting, and MCO-526's business rather than this one's.
 *
 * Plan items come from the materialised `project_demand` rather than by deriving a plan per
 * project: the reporter re-pulls this every ~60s, and deriving is ~285ms per project (MCO-533's
 * measurement), which would be seconds of work per pull on a busy world. The trade is that a
 * project whose plan has not been derived since its requirements changed contributes slightly stale
 * plan items for a cycle. Its *targets* are always current, and a missed item costs one sweep.
 *
 * Tag ids (`#minecraft:planks`) are excluded — a tag is not a thing that can be in a chest.
 */
object GetItemsOfInterestStep : Step<Int, AppFailure.DatabaseError, Map<Int, Set<String>>> {
    override suspend fun process(input: Int) =
        DatabaseSteps.query<Int, Map<Int, Set<String>>>(
            sql = SafeSQL.with(
                """
                WITH targets AS (
                    SELECT rg.project_id, rg.item_id
                    FROM resource_gathering rg
                    JOIN projects p ON p.id = rg.project_id
                    WHERE p.world_id = ?
                ),
                plan_items AS (
                    SELECT pd.project_id, pd.item_id
                    FROM project_demand pd
                    JOIN projects p ON p.id = pd.project_id
                    WHERE p.world_id = ? AND pd.item_id NOT LIKE '#%'
                )
                SELECT project_id, item_id FROM targets
                UNION
                SELECT project_id, item_id FROM plan_items
                """.trimIndent()
            ),
            parameterSetter = { st, worldId ->
                st.setInt(1, worldId)
                st.setInt(2, worldId)
            },
            resultMapper = { rs ->
                buildMap<Int, MutableSet<String>> {
                    while (rs.next()) {
                        getOrPut(rs.getInt("project_id")) { mutableSetOf() }.add(rs.getString("item_id"))
                    }
                }
            },
        ).process(input)
}

// ── The contents push ────────────────────────────────────────────────────────

data class ReportedItem(val itemId: String, val count: Long)

data class ReportedContainer(
    val id: Long,
    val state: String,
    val seenAt: Instant,
    val items: List<ReportedItem>,
)

data class PushContentsInput(
    val worldId: Int,
    val containers: List<ReportedContainer>,
)

data class PushContentsResult(
    /** Containers actually written — those that exist and belong to the token's world. */
    val accepted: Int,
    /** Named containers that are not this world's; silently skipped rather than trusted. */
    val rejected: Int,
    val projectsRecomputed: Int,
)

/**
 * Applies a sweep's reading and re-rolls the affected projects' measurements, in one transaction.
 *
 * The push is an **absolute set for the containers it names**, and names only containers whose
 * contents changed since the reporter's last push. Containers not named keep what they had — that
 * is the whole point of storing per-container contents. One writer means absolute is correct and
 * self-healing: there is nothing to merge and no ordering hazard.
 *
 * Every write is scoped by `world_id`, so a reporter token cannot reach another world's containers
 * even if it names their ids.
 */
object PushContainerContentsStep : Step<PushContentsInput, AppFailure.DatabaseError, PushContentsResult> {

    override suspend fun process(input: PushContentsInput): Result<AppFailure.DatabaseError, PushContentsResult> =
        DatabaseSteps.transaction<PushContentsInput, PushContentsResult> { tx ->
            object : Step<PushContentsInput, AppFailure.DatabaseError, PushContentsResult> {
                override suspend fun process(
                    inner: PushContentsInput,
                ): Result<AppFailure.DatabaseError, PushContentsResult> {
                    // 1. Which named containers are really this world's? Anything else is dropped
                    //    rather than written — the id came from a config file on someone's server.
                    val owned = resolveOwned(tx, inner).getOrElse { return Result.failure(it) }
                    val affectedProjects = owned.values.toSet()

                    // 2. Replace each named container's contents and stamp what the sweep saw.
                    for (container in inner.containers) {
                        if (container.id !in owned) continue
                        clearContents(tx, container.id).getOrElse { return Result.failure(it) }
                        // A container the sweep could not read contributes nothing, so its rows
                        // stay cleared; only a readable one gets contents written back.
                        if (container.state == CONTAINER_STATE_OK) {
                            for (item in container.items) {
                                insertContent(tx, container.id, item, container.seenAt)
                                    .getOrElse { return Result.failure(it) }
                            }
                        }
                        stampTag(tx, container).getOrElse { return Result.failure(it) }
                    }

                    // 3. Re-roll the measurement for every project whose containers moved.
                    for (projectId in affectedProjects) {
                        recompute(tx, projectId).getOrElse { return Result.failure(it) }
                    }

                    return Result.success(
                        PushContentsResult(
                            accepted = owned.size,
                            rejected = inner.containers.size - owned.size,
                            projectsRecomputed = affectedProjects.size,
                        )
                    )
                }
            }
        }.process(input)

    /** Named container ids that exist in this world, mapped to the project they are tagged to. */
    private suspend fun resolveOwned(
        tx: TransactionConnection,
        input: PushContentsInput,
    ): Result<AppFailure.DatabaseError, Map<Long, Int>> {
        if (input.containers.isEmpty()) return Result.success(emptyMap())
        val ids = input.containers.map { it.id }
        val placeholders = ids.joinToString(",") { "?" }
        return DatabaseSteps.query<Unit, Map<Long, Int>>(
            sql = SafeSQL.select(
                "SELECT id, project_id FROM container_tags WHERE world_id = ? AND id IN ($placeholders)"
            ),
            parameterSetter = { st, _ ->
                st.setInt(1, input.worldId)
                ids.forEachIndexed { i, id -> st.setLong(i + 2, id) }
            },
            resultMapper = { rs ->
                buildMap { while (rs.next()) put(rs.getLong("id"), rs.getInt("project_id")) }
            },
            transactionConnection = tx,
        ).process(Unit)
    }

    private suspend fun clearContents(tx: TransactionConnection, tagId: Long) =
        DatabaseSteps.update<Long>(
            sql = SafeSQL.delete("DELETE FROM container_contents WHERE container_tag_id = ?"),
            parameterSetter = { st, id -> st.setLong(1, id) },
            transactionConnection = tx,
        ).process(tagId)

    private suspend fun insertContent(
        tx: TransactionConnection,
        tagId: Long,
        item: ReportedItem,
        seenAt: Instant,
    ) = DatabaseSteps.update<Unit>(
        sql = SafeSQL.insert(
            """
            INSERT INTO container_contents (container_tag_id, item_id, count, seen_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (container_tag_id, item_id) DO UPDATE
            SET count = EXCLUDED.count, seen_at = EXCLUDED.seen_at
            """.trimIndent()
        ),
        parameterSetter = { st, _ ->
            st.setLong(1, tagId)
            st.setString(2, item.itemId)
            st.setLong(3, item.count)
            st.setObject(4, OffsetDateTime.ofInstant(seenAt, ZoneOffset.UTC))
        },
        transactionConnection = tx,
    ).process(Unit)

    /** `state` and `last_seen_at` are the reporter's to write, and this is where it writes them. */
    private suspend fun stampTag(tx: TransactionConnection, container: ReportedContainer) =
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.update(
                "UPDATE container_tags SET state = ?, last_seen_at = ? WHERE id = ?"
            ),
            parameterSetter = { st, _ ->
                st.setString(1, container.state)
                st.setObject(2, OffsetDateTime.ofInstant(container.seenAt, ZoneOffset.UTC))
                st.setLong(3, container.id)
            },
            transactionConnection = tx,
        ).process(Unit)

    private suspend fun recompute(tx: TransactionConnection, projectId: Int) =
        RecomputeMeasurementStep.within(tx).process(projectId)
}

const val CONTAINER_STATE_OK = "ok"

/**
 * Rebuilds one project's measurement rows from the container contents underneath it.
 *
 * Only containers in state `ok` contribute. An `unreadable` container has never been read and a
 * `missing` one is gone, so counting either would assert stock that is not there; how many are in
 * those states is told from `container_tags`, which is where state lives.
 *
 * Delete-then-insert rather than upsert because an item can leave a project's storage entirely, and
 * an upsert would leave the stale row behind claiming stock that no longer exists.
 */
object RecomputeMeasurementStep : Step<Int, AppFailure.DatabaseError, Int> {

    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> =
        DatabaseSteps.transaction<Int, Int> { tx -> within(tx) }.process(input)

    /** The same recompute, joined into a caller's transaction. */
    fun within(tx: TransactionConnection): Step<Int, AppFailure.DatabaseError, Int> =
        object : Step<Int, AppFailure.DatabaseError, Int> {
            override suspend fun process(projectId: Int): Result<AppFailure.DatabaseError, Int> {
                DatabaseSteps.update<Int>(
                    sql = SafeSQL.delete("DELETE FROM resource_gathering_measurement WHERE project_id = ?"),
                    parameterSetter = { st, id -> st.setInt(1, id) },
                    transactionConnection = tx,
                ).process(projectId).getOrElse { return Result.failure(it) }

                return DatabaseSteps.update<Int>(
                    sql = SafeSQL.insert(
                        """
                        INSERT INTO resource_gathering_measurement
                            (project_id, item_id, measured, container_count, oldest_seen_at, updated_at)
                        SELECT ct.project_id,
                               cc.item_id,
                               SUM(cc.count),
                               COUNT(DISTINCT ct.id),
                               MIN(cc.seen_at),
                               CURRENT_TIMESTAMP
                        FROM container_tags ct
                        JOIN container_contents cc ON cc.container_tag_id = ct.id
                        WHERE ct.project_id = ? AND ct.state = 'ok'
                        GROUP BY ct.project_id, cc.item_id
                        """.trimIndent()
                    ),
                    parameterSetter = { st, id -> st.setInt(1, id) },
                    transactionConnection = tx,
                ).process(projectId)
            }
        }
}

// ── The HUD's read ───────────────────────────────────────────────────────────

data class WorldStorageRow(
    val projectId: Int,
    val itemId: String,
    val measured: Long,
    val containerCount: Int,
    val oldestSeenAt: Instant?,
)

private fun ResultSet.mapToStorageRow() = WorldStorageRow(
    projectId = getInt("project_id"),
    itemId = getString("item_id"),
    measured = getLong("measured"),
    containerCount = getInt("container_count"),
    oldestSeenAt = getTimestamp("oldest_seen_at")?.toInstant(),
)

/**
 * Every measured count in a world, in **one indexed query** — no plan derivation, no per-project
 * assembly. This is the HUD's frequent poll (~10s per player), and it is world-wide rather than
 * per-project so one poll serves both HUD modes and whichever project is active.
 */
object GetWorldStorageStep : Step<Int, AppFailure.DatabaseError, List<WorldStorageRow>> {
    override suspend fun process(input: Int) =
        DatabaseSteps.query<Int, List<WorldStorageRow>>(
            sql = SafeSQL.select(
                """
                SELECT m.project_id, m.item_id, m.measured, m.container_count, m.oldest_seen_at
                FROM resource_gathering_measurement m
                JOIN projects p ON p.id = m.project_id
                WHERE p.world_id = ?
                ORDER BY m.project_id, m.item_id
                """.trimIndent()
            ),
            parameterSetter = { st, worldId -> st.setInt(1, worldId) },
            resultMapper = { rs -> buildList { while (rs.next()) add(rs.mapToStorageRow()) } },
        ).process(input)
}

data class ContainerPositionKey(
    val worldId: Int,
    val dimension: String,
    val x: Int,
    val y: Int,
    val z: Int,
)

/**
 * The project currently tagged at a position, or NotFound if none is. Read *before* an upsert, so
 * the caller knows whether a re-tag is moving the container's stored contents to a new project and
 * therefore which rollups have gone stale.
 */
object FindContainerTagProjectByPositionStep :
    Step<ContainerPositionKey, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: ContainerPositionKey): Result<AppFailure.DatabaseError, Int> =
        DatabaseSteps.query<ContainerPositionKey, Int?>(
            sql = SafeSQL.select(
                """
                SELECT project_id
                FROM container_tags
                WHERE world_id = ? AND dimension = ? AND x = ? AND y = ? AND z = ?
                """.trimIndent()
            ),
            parameterSetter = { st, k ->
                st.setInt(1, k.worldId)
                st.setString(2, k.dimension)
                st.setInt(3, k.x)
                st.setInt(4, k.y)
                st.setInt(5, k.z)
            },
            resultMapper = { if (it.next()) it.getInt("project_id") else null },
        ).process(input).flatMap {
            if (it == null) Result.failure(AppFailure.DatabaseError.NotFound) else Result.success(it)
        }
}

/** The project a tag belongs to, for re-rolling its measurement after the tag is deleted. */
object GetContainerTagProjectStep : Step<ContainerTagKey, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: ContainerTagKey): Result<AppFailure.DatabaseError, Int> =
        DatabaseSteps.query<ContainerTagKey, Int?>(
            sql = SafeSQL.select("SELECT project_id FROM container_tags WHERE world_id = ? AND id = ?"),
            parameterSetter = { st, key ->
                st.setInt(1, key.worldId)
                st.setLong(2, key.id)
            },
            resultMapper = { if (it.next()) it.getInt("project_id") else null },
        ).process(input).flatMap {
            if (it == null) Result.failure(AppFailure.DatabaseError.NotFound) else Result.success(it)
        }
}
