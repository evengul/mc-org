package app.mcorg.api

import app.mcorg.config.CacheManager
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

// ── api_token ────────────────────────────────────────────────────────────────

data class CreateApiTokenInput(
    val userId: Int,
    val tokenHash: String,
    val name: String?,
    val expiresAt: Instant?,
)

/** Persists a new bearer token (its hash only). Returns affected-row count. */
object CreateApiTokenStep : Step<CreateApiTokenInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: CreateApiTokenInput) =
        DatabaseSteps.update<CreateApiTokenInput>(
            sql = SafeSQL.insert(
                "INSERT INTO api_token (user_id, token_hash, name, expires_at) VALUES (?, ?, ?, ?)"
            ),
            parameterSetter = { st, i ->
                st.setInt(1, i.userId)
                st.setString(2, i.tokenHash)
                if (i.name != null) st.setString(3, i.name) else st.setNull(3, java.sql.Types.VARCHAR)
                if (i.expiresAt != null) {
                    st.setObject(4, OffsetDateTime.ofInstant(i.expiresAt, ZoneOffset.UTC))
                } else {
                    st.setNull(4, java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                }
            },
        ).process(input)
}

/**
 * Resolves the user behind a live bearer token by its hash: not revoked and not past expiry.
 * Fails with [AppFailure.DatabaseError.NotFound] when no live token matches.
 */
object LookupApiTokenUserStep : Step<String, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: String): Result<AppFailure.DatabaseError, Int> =
        DatabaseSteps.query<String, Int?>(
            sql = SafeSQL.select(
                """
                SELECT user_id
                FROM api_token
                WHERE token_hash = ?
                  AND revoked_at IS NULL
                  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                """.trimIndent()
            ),
            parameterSetter = { st, hash -> st.setString(1, hash) },
            resultMapper = { if (it.next()) it.getInt("user_id") else null },
        ).process(input).flatMap {
            if (it == null) Result.failure(AppFailure.DatabaseError.NotFound) else Result.success(it)
        }
}

/** Best-effort bump of last_used_at on a successful auth. */
object TouchApiTokenStep : Step<String, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: String) =
        DatabaseSteps.update<String>(
            sql = SafeSQL.update("UPDATE api_token SET last_used_at = CURRENT_TIMESTAMP WHERE token_hash = ?"),
            parameterSetter = { st, hash -> st.setString(1, hash) },
        ).process(input)
}

/** Revokes a live token by its hash. Returns affected-row count (0 if already revoked/absent). */
object RevokeApiTokenStep : Step<String, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: String) =
        DatabaseSteps.update<String>(
            sql = SafeSQL.update(
                "UPDATE api_token SET revoked_at = CURRENT_TIMESTAMP WHERE token_hash = ? AND revoked_at IS NULL"
            ),
            parameterSetter = { st, hash -> st.setString(1, hash) },
        ).process(input)
}

/**
 * Whether a user is globally banned. Reuses the exact `global_user_roles` 'banned' lookup and the
 * shared [CacheManager.bannedUsers] cache that BannedPlugin uses, so the API shares one ban truth.
 */
object IsUserBannedStep : Step<Int, AppFailure.DatabaseError, Boolean> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Boolean> {
        CacheManager.bannedUsers.getIfPresent(input)?.let { return Result.success(it) }
        val result = DatabaseSteps.query<Int, Boolean>(
            sql = SafeSQL.select("SELECT 1 FROM global_user_roles WHERE user_id = ? AND role = 'banned'"),
            parameterSetter = { st, id -> st.setInt(1, id) },
            resultMapper = { it.next() },
        ).process(input)
        if (result is Result.Success) CacheManager.bannedUsers.put(input, result.value)
        return result
    }
}

/** Whether a user holds the demo role (mirrors the web app's demo-user detection). */
object IsDemoUserStep : Step<Int, AppFailure.DatabaseError, Boolean> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Boolean> =
        DatabaseSteps.query<Int, Boolean>(
            sql = SafeSQL.select("SELECT 1 FROM global_user_roles WHERE user_id = ? AND role = 'demo_user'"),
            parameterSetter = { st, id -> st.setInt(1, id) },
            resultMapper = { it.next() },
        ).process(input)
}

/** Minecraft username for a user id, used for event attribution and the poll response. */
object GetUsernameByIdStep : Step<Int, AppFailure.DatabaseError, String> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, String> =
        DatabaseSteps.query<Int, String?>(
            sql = SafeSQL.select("SELECT username FROM minecraft_profiles WHERE user_id = ?"),
            parameterSetter = { st, id -> st.setInt(1, id) },
            resultMapper = { if (it.next()) it.getString("username") else null },
        ).process(input).flatMap {
            if (it == null) Result.failure(AppFailure.DatabaseError.NotFound) else Result.success(it)
        }
}

// ── device_code ──────────────────────────────────────────────────────────────

data class CreateDeviceCodeInput(
    val deviceCode: String,
    val userCode: String,
    val expiresAt: Instant,
    val intervalSeconds: Int,
)

object CreateDeviceCodeStep : Step<CreateDeviceCodeInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: CreateDeviceCodeInput) =
        DatabaseSteps.update<CreateDeviceCodeInput>(
            sql = SafeSQL.insert(
                """
                INSERT INTO device_code (device_code, user_code, status, expires_at, interval_seconds)
                VALUES (?, ?, 'pending', ?, ?)
                """.trimIndent()
            ),
            parameterSetter = { st, i ->
                st.setString(1, i.deviceCode)
                st.setString(2, i.userCode)
                st.setObject(3, OffsetDateTime.ofInstant(i.expiresAt, ZoneOffset.UTC))
                st.setInt(4, i.intervalSeconds)
            },
        ).process(input)
}

/** Row used by the browser /link approval flow (looked up by the user-typed code). */
data class DeviceCodeApprovalRow(val id: Long, val status: String, val expiresAt: Instant)

object GetDeviceCodeByUserCodeStep : Step<String, AppFailure.DatabaseError, DeviceCodeApprovalRow> {
    override suspend fun process(input: String): Result<AppFailure.DatabaseError, DeviceCodeApprovalRow> =
        DatabaseSteps.query<String, DeviceCodeApprovalRow?>(
            sql = SafeSQL.select(
                "SELECT id, status, expires_at FROM device_code WHERE user_code = ?"
            ),
            parameterSetter = { st, code -> st.setString(1, code) },
            resultMapper = {
                if (it.next()) DeviceCodeApprovalRow(
                    id = it.getLong("id"),
                    status = it.getString("status"),
                    expiresAt = it.getTimestamp("expires_at").toInstant(),
                ) else null
            },
        ).process(input).flatMap {
            if (it == null) Result.failure(AppFailure.DatabaseError.NotFound) else Result.success(it)
        }
}

data class ApproveDeviceCodeInput(val userCode: String, val userId: Int)

/** Binds a pending, unexpired code to the approving user. Returns affected-row count. */
object ApproveDeviceCodeStep : Step<ApproveDeviceCodeInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: ApproveDeviceCodeInput) =
        DatabaseSteps.update<ApproveDeviceCodeInput>(
            sql = SafeSQL.update(
                """
                UPDATE device_code
                SET status = 'approved', user_id = ?
                WHERE user_code = ? AND status = 'pending' AND expires_at > CURRENT_TIMESTAMP
                """.trimIndent()
            ),
            parameterSetter = { st, i ->
                st.setInt(1, i.userId)
                st.setString(2, i.userCode)
            },
        ).process(input)
}

/** Full row used by the mod poll endpoint (looked up by the opaque device code). */
data class DeviceCodePollRow(
    val id: Long,
    val userId: Int?,
    val status: String,
    val expiresAt: Instant,
    val intervalSeconds: Int,
    val lastPolledAt: Instant?,
    val tokenIssued: Boolean,
)

object GetDeviceCodeForPollStep : Step<String, AppFailure.DatabaseError, DeviceCodePollRow> {
    override suspend fun process(input: String): Result<AppFailure.DatabaseError, DeviceCodePollRow> =
        DatabaseSteps.query<String, DeviceCodePollRow?>(
            sql = SafeSQL.select(
                """
                SELECT id, user_id, status, expires_at, interval_seconds, last_polled_at, token_issued
                FROM device_code
                WHERE device_code = ?
                """.trimIndent()
            ),
            parameterSetter = { st, code -> st.setString(1, code) },
            resultMapper = {
                if (it.next()) {
                    val uid = it.getInt("user_id")
                    DeviceCodePollRow(
                        id = it.getLong("id"),
                        userId = if (it.wasNull()) null else uid,
                        status = it.getString("status"),
                        expiresAt = it.getTimestamp("expires_at").toInstant(),
                        intervalSeconds = it.getInt("interval_seconds"),
                        lastPolledAt = it.getTimestamp("last_polled_at")?.toInstant(),
                        tokenIssued = it.getBoolean("token_issued"),
                    )
                } else null
            },
        ).process(input).flatMap {
            if (it == null) Result.failure(AppFailure.DatabaseError.NotFound) else Result.success(it)
        }
}

/** Stamps last_polled_at for slow_down enforcement. */
object TouchDeviceCodePolledStep : Step<String, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: String) =
        DatabaseSteps.update<String>(
            sql = SafeSQL.update("UPDATE device_code SET last_polled_at = CURRENT_TIMESTAMP WHERE device_code = ?"),
            parameterSetter = { st, code -> st.setString(1, code) },
        ).process(input)
}

/** Marks a code expired (lazy expiry on poll). */
object ExpireDeviceCodeStep : Step<String, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: String) =
        DatabaseSteps.update<String>(
            sql = SafeSQL.update("UPDATE device_code SET status = 'expired' WHERE device_code = ?"),
            parameterSetter = { st, code -> st.setString(1, code) },
        ).process(input)
}

/**
 * Atomically claims the one-time token issuance for an approved code. Returns 1 exactly once (the
 * winning poll), 0 on every subsequent call — so a device code mints its token only once.
 */
object ClaimDeviceCodeTokenStep : Step<String, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: String) =
        DatabaseSteps.update<String>(
            sql = SafeSQL.update(
                "UPDATE device_code SET token_issued = TRUE WHERE device_code = ? AND status = 'approved' AND token_issued = FALSE"
            ),
            parameterSetter = { st, code -> st.setString(1, code) },
        ).process(input)
}

// ── container_tags ───────────────────────────────────────────────────────────

/**
 * A tagged container as the API renders it. [taggedByName] is the tagger's Minecraft username
 * (null when the profile is gone); [lastSeenAt] and [state] are the reporter's to write.
 */
data class ContainerTagRow(
    val id: Long,
    val projectId: Int,
    val dimension: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val groupKey: String,
    val kind: String,
    val taggedByName: String?,
    val taggedAt: Instant,
    val lastSeenAt: Instant?,
    val state: String,
)

private fun ResultSet.mapToContainerTag() = ContainerTagRow(
    id = getLong("id"),
    projectId = getInt("project_id"),
    dimension = getString("dimension"),
    x = getInt("x"),
    y = getInt("y"),
    z = getInt("z"),
    groupKey = getString("group_key"),
    kind = getString("kind"),
    taggedByName = getString("tagged_by_name"),
    taggedAt = getTimestamp("tagged_at").toInstant(),
    lastSeenAt = getTimestamp("last_seen_at")?.toInstant(),
    state = getString("state"),
)

private const val CONTAINER_TAG_COLUMNS =
    "ct.id, ct.project_id, ct.dimension, ct.x, ct.y, ct.z, ct.group_key, ct.kind, " +
        "ct.tagged_at, ct.last_seen_at, ct.state, mp.username AS tagged_by_name"

/** Every tagged container in a world, oldest first. */
object ListContainerTagsStep : Step<Int, AppFailure.DatabaseError, List<ContainerTagRow>> {
    override suspend fun process(input: Int) =
        DatabaseSteps.query<Int, List<ContainerTagRow>>(
            sql = SafeSQL.select(
                """
                SELECT $CONTAINER_TAG_COLUMNS
                FROM container_tags ct
                LEFT JOIN minecraft_profiles mp ON mp.user_id = ct.tagged_by
                WHERE ct.world_id = ?
                ORDER BY ct.tagged_at, ct.id
                """.trimIndent()
            ),
            parameterSetter = { st, worldId -> st.setInt(1, worldId) },
            resultMapper = { rs -> buildList { while (rs.next()) add(rs.mapToContainerTag()) } },
        ).process(input)
}

/** Addresses one tag within its world, so a member of world A cannot reach world B's rows. */
data class ContainerTagKey(val worldId: Int, val id: Long)

data class UpsertContainerTagInput(
    val worldId: Int,
    val projectId: Int,
    val dimension: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val groupKey: String,
    val kind: String,
    val taggedBy: Int,
)

/**
 * Creates a tag, or moves the one already at that position to the new project. Keyed on the
 * `(world_id, dimension, x, y, z)` unique constraint, so re-tagging is one row, not two.
 *
 * **Re-tagging the same kind keeps the reading; changing kind discards it.** Re-tagging is normally
 * the same physical container being pointed at a different project, so its last reading stays valid
 * and simply counts toward the new project — the next sweep re-filters it to that project's items.
 * But if the `kind` changed, the block itself changed: someone broke the chest and put a barrel
 * there. Keeping `state = 'ok'` then would present a reading of a container that no longer exists
 * as current, and phase B's contents hang off this row id, so those stale contents would keep
 * counting. So the two columns follow `kind`, and only `kind`.
 *
 * The insert and the read-back are one statement. Split in two they race: a concurrent DELETE
 * between them makes a successful write report a 500. A data-modifying CTE closes the window, and
 * the join is the only reason a read-back was needed at all.
 */
object UpsertContainerTagStep : Step<UpsertContainerTagInput, AppFailure.DatabaseError, ContainerTagRow> {
    override suspend fun process(input: UpsertContainerTagInput): Result<AppFailure.DatabaseError, ContainerTagRow> =
        DatabaseSteps.query<UpsertContainerTagInput, ContainerTagRow?>(
            sql = SafeSQL.with(
                """
                WITH upserted AS (
                    INSERT INTO container_tags
                        (world_id, project_id, dimension, x, y, z, group_key, kind, tagged_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (world_id, dimension, x, y, z) DO UPDATE
                    SET project_id   = EXCLUDED.project_id,
                        kind         = EXCLUDED.kind,
                        group_key    = EXCLUDED.group_key,
                        tagged_by    = EXCLUDED.tagged_by,
                        tagged_at    = CURRENT_TIMESTAMP,
                        state        = CASE WHEN container_tags.kind IS DISTINCT FROM EXCLUDED.kind
                                            THEN 'unreadable' ELSE container_tags.state END,
                        last_seen_at = CASE WHEN container_tags.kind IS DISTINCT FROM EXCLUDED.kind
                                            THEN NULL ELSE container_tags.last_seen_at END
                    RETURNING id, project_id, dimension, x, y, z, group_key, kind,
                              tagged_by, tagged_at, last_seen_at, state
                )
                SELECT ct.id, ct.project_id, ct.dimension, ct.x, ct.y, ct.z, ct.group_key, ct.kind,
                       ct.tagged_at, ct.last_seen_at, ct.state, mp.username AS tagged_by_name
                FROM upserted ct
                LEFT JOIN minecraft_profiles mp ON mp.user_id = ct.tagged_by
                """.trimIndent()
            ),
            parameterSetter = { st, i ->
                st.setInt(1, i.worldId)
                st.setInt(2, i.projectId)
                st.setString(3, i.dimension)
                st.setInt(4, i.x)
                st.setInt(5, i.y)
                st.setInt(6, i.z)
                st.setString(7, i.groupKey)
                st.setString(8, i.kind)
                st.setInt(9, i.taggedBy)
            },
            resultMapper = { if (it.next()) it.mapToContainerTag() else null },
        ).process(input).flatMap {
            if (it == null) Result.failure(AppFailure.DatabaseError.NoIdReturned) else Result.success(it)
        }
}

/**
 * Untags a container. Returns affected-row count (0 when the tag is absent or belongs to another
 * world).
 *
 * MCO-532's `container_contents` will hang off this row with `ON DELETE CASCADE`, so once that
 * table exists the chest's contribution to the measurement disappears with the tag and the reporter
 * never has to be told. That table does not exist yet; nothing cascades today.
 */
object DeleteContainerTagStep : Step<ContainerTagKey, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: ContainerTagKey) =
        DatabaseSteps.update<ContainerTagKey>(
            sql = SafeSQL.delete("DELETE FROM container_tags WHERE world_id = ? AND id = ?"),
            parameterSetter = { st, key ->
                st.setInt(1, key.worldId)
                st.setLong(2, key.id)
            },
        ).process(input)
}
