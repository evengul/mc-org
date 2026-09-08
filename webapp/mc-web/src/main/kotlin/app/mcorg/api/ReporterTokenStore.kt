package app.mcorg.api

import app.mcorg.pipeline.Step
import app.mcorg.logging.redacted
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import java.sql.ResultSet
import java.time.Instant

/**
 * Reporter tokens (MCO-531) — the credential the mod's server half authenticates with.
 *
 * Deliberately a table of its own rather than a nullable `world_id` on `api_token`: the player-token
 * plugin resolves a hash to a *user id*, and a reporter has no user. Kept apart, that plugin cannot
 * resolve a reporter token at all, so a reporter is rejected from every player route by
 * construction. See `V2_68_0__create_reporter_token.sql` for the full argument.
 */

/**
 * A reporter token as world settings renders it. Carries no secret — after minting, only the hash
 * exists, so there is nothing here to redact.
 */
data class ReporterTokenRow(
    val id: Long,
    val worldId: Int,
    val name: String?,
    val createdByName: String?,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    val reporterVersion: String?,
) {
    /** True until the reporter's first contents push stamps `last_used_at` (MCO-532). */
    val neverConnected: Boolean get() = lastUsedAt == null
}

/**
 * The one moment a reporter token exists in plaintext outside the operator's config file: the
 * response to the mint that created it. Only [row] is persisted, keyed by the token's hash — so if
 * this is lost, the only remedy is to revoke and mint again, which is the intended trade.
 *
 * The explicit [toString] is required of every secret-bearing type (see `mc-web/CLAUDE.md`): the
 * compiler-generated one prints every field, leaving the type one `logger.debug("$thing")` away
 * from writing a live credential to the logs.
 */
data class MintedReporterToken(
    val row: ReporterTokenRow,
    val token: String,
) {
    override fun toString(): String = "MintedReporterToken(row=$row, token=${redacted(token)})"
}

private fun ResultSet.mapToReporterToken() = ReporterTokenRow(
    id = getLong("id"),
    worldId = getInt("world_id"),
    name = getString("name"),
    createdByName = getString("created_by_name"),
    createdAt = getTimestamp("created_at").toInstant(),
    lastUsedAt = getTimestamp("last_used_at")?.toInstant(),
    reporterVersion = getString("reporter_version"),
)

private const val REPORTER_TOKEN_COLUMNS =
    "rt.id, rt.world_id, rt.name, rt.created_at, rt.last_used_at, rt.reporter_version, " +
        "mp.username AS created_by_name"

data class CreateReporterTokenInput(
    val worldId: Int,
    val tokenHash: String,
    val name: String?,
    val createdBy: Int,
)

/**
 * Persists a freshly minted token (its hash only) and returns the row for display, in **one
 * statement**.
 *
 * The insert and the read-back must not be two round trips. The caller has already generated the
 * raw token by this point, and a second query could miss the row — a concurrent revoke between the
 * two, and a read-back filtering `revoked_at IS NULL` returns nothing. The mint would
 * then report failure while a live row existed whose plaintext was shown to nobody: unusable,
 * unrecoverable, and visible only as a mystery entry in the list. A data-modifying CTE closes that
 * window and halves the round trips; the join is the only reason a read-back was needed at all.
 */
object CreateReporterTokenStep : Step<CreateReporterTokenInput, AppFailure.DatabaseError, ReporterTokenRow> {
    override suspend fun process(input: CreateReporterTokenInput): Result<AppFailure.DatabaseError, ReporterTokenRow> =
        DatabaseSteps.query<CreateReporterTokenInput, ReporterTokenRow?>(
            sql = SafeSQL.with(
                """
                WITH inserted AS (
                    INSERT INTO reporter_token (world_id, token_hash, name, created_by)
                    VALUES (?, ?, ?, ?)
                    RETURNING id, world_id, name, created_by, created_at, last_used_at, reporter_version
                )
                SELECT rt.id, rt.world_id, rt.name, rt.created_at, rt.last_used_at,
                       rt.reporter_version, mp.username AS created_by_name
                FROM inserted rt
                LEFT JOIN minecraft_profiles mp ON mp.user_id = rt.created_by
                """.trimIndent()
            ),
            parameterSetter = { st, i ->
                st.setInt(1, i.worldId)
                st.setString(2, i.tokenHash)
                if (i.name != null) st.setString(3, i.name) else st.setNull(3, java.sql.Types.VARCHAR)
                st.setInt(4, i.createdBy)
            },
            resultMapper = { if (it.next()) it.mapToReporterToken() else null },
        ).process(input).flatMap {
            if (it == null) Result.failure(AppFailure.DatabaseError.NoIdReturned) else Result.success(it)
        }
}

/** Addresses one token within its world, so an admin of world A cannot reach world B's rows. */
data class ReporterTokenKey(val worldId: Int, val id: Long)

/**
 * A world's live reporter tokens, newest first. Revoked tokens are omitted rather than shown struck
 * through: the question this list answers is "what can currently write to this world".
 */
object ListReporterTokensStep : Step<Int, AppFailure.DatabaseError, List<ReporterTokenRow>> {
    override suspend fun process(input: Int) =
        DatabaseSteps.query<Int, List<ReporterTokenRow>>(
            sql = SafeSQL.select(
                """
                SELECT $REPORTER_TOKEN_COLUMNS
                FROM reporter_token rt
                LEFT JOIN minecraft_profiles mp ON mp.user_id = rt.created_by
                WHERE rt.world_id = ? AND rt.revoked_at IS NULL
                ORDER BY rt.created_at DESC, rt.id DESC
                """.trimIndent()
            ),
            parameterSetter = { st, worldId -> st.setInt(1, worldId) },
            resultMapper = { rs -> buildList { while (rs.next()) add(rs.mapToReporterToken()) } },
        ).process(input)
}

/**
 * Revokes a token. Returns affected-row count (0 when absent, already revoked, or belonging to
 * another world). The row is kept rather than deleted so a revoked credential can never be re-minted
 * onto the same hash.
 */
object RevokeReporterTokenStep : Step<ReporterTokenKey, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: ReporterTokenKey) =
        DatabaseSteps.update<ReporterTokenKey>(
            sql = SafeSQL.update(
                """
                UPDATE reporter_token
                SET revoked_at = CURRENT_TIMESTAMP
                WHERE world_id = ? AND id = ? AND revoked_at IS NULL
                """.trimIndent()
            ),
            parameterSetter = { st, key ->
                st.setInt(1, key.worldId)
                st.setLong(2, key.id)
            },
        ).process(input)
}
