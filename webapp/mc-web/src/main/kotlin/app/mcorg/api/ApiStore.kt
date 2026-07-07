package app.mcorg.api

import app.mcorg.config.CacheManager
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
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
