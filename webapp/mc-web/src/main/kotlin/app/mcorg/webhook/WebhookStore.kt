package app.mcorg.webhook

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * All database access for webhook subscriptions and the delivery outbox. Used by
 * [WebhookFanoutConsumer] (enqueue) and [WebhookDeliveryPoller] (drain). Every call swallows and
 * logs database failures, returning a safe default — webhook delivery is best-effort and must never
 * propagate into the event-bus dispatch or the polling loop that drive it.
 */
object WebhookStore {
    private val logger = LoggerFactory.getLogger(WebhookStore::class.java)
    private val json = Json
    private val stringListSerializer = ListSerializer(String.serializer())

    /** Active subscriptions for [worldId]. Filter matching ([eventMatchesFilter]) happens in-app. */
    suspend fun findActiveSubscriptions(worldId: Int): List<WebhookSubscription> {
        val result = DatabaseSteps.query<Unit, List<WebhookSubscription>>(
            sql = SafeSQL.select(
                """
                SELECT id, world_id, callback_url, secret, event_filter::text AS event_filter, consecutive_failures
                FROM webhook_subscriptions
                WHERE world_id = ? AND active = true
                """.trimIndent()
            ),
            parameterSetter = { statement, _ -> statement.setInt(1, worldId) },
            resultMapper = { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            WebhookSubscription(
                                id = rs.getInt("id"),
                                worldId = rs.getInt("world_id"),
                                callbackUrl = rs.getString("callback_url"),
                                secret = rs.getString("secret"),
                                eventFilter = parseFilter(rs.getString("event_filter")),
                                active = true,
                                consecutiveFailures = rs.getInt("consecutive_failures"),
                            )
                        )
                    }
                }
            },
        ).process(Unit)
        return result.orEmpty("findActiveSubscriptions(world=$worldId)")
    }

    /** Append one outbox row (status PENDING, due immediately) for [subscriptionId]. */
    suspend fun enqueueDelivery(subscriptionId: Int, eventType: String, payload: String) {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                """
                INSERT INTO webhook_deliveries (subscription_id, event_type, payload)
                VALUES (?, ?, ?::jsonb)
                """.trimIndent()
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, subscriptionId)
                statement.setString(2, eventType)
                statement.setString(3, payload)
            },
        ).process(Unit).logIfFailed("enqueueDelivery(sub=$subscriptionId, $eventType)")
    }

    /**
     * How long a claim is honoured before another poller may take the row back.
     *
     * Comfortably longer than a delivery attempt — the HTTP client's request timeout is 5s — so a
     * slow-but-alive delivery is never stolen mid-flight. Short enough that a machine killed
     * between claiming and delivering does not strand its rows for long.
     */
    private const val CLAIM_LEASE_MINUTES = 5

    /**
     * Atomically claims up to [limit] due rows and returns them, oldest first and grouped by
     * subscription. Joined with the subscription's callback URL + secret so the poller has
     * everything it needs from one statement.
     *
     * This replaced a plain SELECT (MCO-357). The row used to be mutated only *after* the HTTP
     * POST returned, so two pollers scanning concurrently would both see the same rows and both
     * deliver them. `fly.toml` runs one machine today, but `auto_start_machines = true`, and the
     * duplicate would appear only in production and only under scale-out.
     *
     * Written as `UPDATE ... FROM (SELECT ... FOR UPDATE SKIP LOCKED) ... RETURNING` rather than a
     * transaction held open across the delivery. That distinction matters: holding row locks and a
     * pooled connection for the duration of a 5s HTTP call is how a handful of slow endpoints
     * exhaust the pool. Here the lock lives only for the claim, and `IN_FLIGHT` carries the
     * exclusion afterwards.
     *
     * `SKIP LOCKED` means a second poller racing on the same rows takes the *next* ones instead of
     * blocking behind the first.
     */
    suspend fun claimDueDeliveries(limit: Int = 200): List<DueDelivery> {
        val result = DatabaseSteps.query<Unit, List<DueDelivery>>(
            sql = SafeSQL.with(
                """
                WITH claimed AS (
                    UPDATE webhook_deliveries d
                    SET status = 'IN_FLIGHT',
                        claimed_at = CURRENT_TIMESTAMP,
                        updated_at = CURRENT_TIMESTAMP
                    FROM (
                        SELECT d2.id
                        FROM webhook_deliveries d2
                        JOIN webhook_subscriptions s2 ON s2.id = d2.subscription_id
                        WHERE s2.active = true
                          AND (
                            (d2.status = 'PENDING' AND d2.next_attempt_at <= CURRENT_TIMESTAMP)
                            -- Reclaim: a poller that died mid-delivery left these behind, and
                            -- nobody else will ever come back for them.
                            OR (d2.status = 'IN_FLIGHT'
                                AND d2.claimed_at < CURRENT_TIMESTAMP - INTERVAL '$CLAIM_LEASE_MINUTES minutes')
                          )
                        ORDER BY d2.subscription_id, d2.created_at, d2.id
                        LIMIT ?
                        FOR UPDATE OF d2 SKIP LOCKED
                    ) sel
                    WHERE d.id = sel.id
                    RETURNING d.id, d.subscription_id, d.event_type, d.payload::text AS payload, d.attempts
                )
                SELECT c.id, c.subscription_id, c.event_type, c.payload, c.attempts,
                       s.callback_url, s.secret
                FROM claimed c
                JOIN webhook_subscriptions s ON s.id = c.subscription_id
                ORDER BY c.subscription_id, c.id
                """.trimIndent()
            ),
            parameterSetter = { statement, _ -> statement.setInt(1, limit) },
            resultMapper = { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            DueDelivery(
                                id = rs.getLong("id"),
                                subscriptionId = rs.getInt("subscription_id"),
                                callbackUrl = rs.getString("callback_url"),
                                secret = rs.getString("secret"),
                                eventType = rs.getString("event_type"),
                                payload = rs.getString("payload"),
                                attempts = rs.getInt("attempts"),
                            )
                        )
                    }
                }
            },
        ).process(Unit)
        return result.orEmpty("claimDueDeliveries")
    }

    /** Mark a batch delivered. */
    suspend fun markDelivered(ids: List<Long>) {
        if (ids.isEmpty()) return
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.update(
                """
                UPDATE webhook_deliveries
                SET status = 'DELIVERED', delivered_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP,
                    last_error = NULL, claimed_at = NULL
                WHERE id = ANY(?)
                """.trimIndent()
            ),
            parameterSetter = { statement, _ ->
                statement.setArray(1, statement.connection.createArrayOf("bigint", ids.toTypedArray()))
            },
        ).process(Unit).logIfFailed("markDelivered(${ids.size})")
    }

    /**
     * Account for a failed batch: bump each row's attempt count, then either reschedule it
     * (PENDING) with backoff or give up (FAILED) once it has used all [maxAttempts]. Backoff keys
     * off each row's own attempt count, so a freshly-enqueued row batched alongside an
     * already-retried one still gets the full retry budget.
     */
    suspend fun failOrReschedule(ids: List<Long>, maxAttempts: Int, error: String) {
        if (ids.isEmpty()) return
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.update(
                """
                UPDATE webhook_deliveries
                SET attempts = attempts + 1,
                    last_error = ?,
                    updated_at = CURRENT_TIMESTAMP,
                    -- Releases the claim: the row is either retryable by anyone or finished.
                    claimed_at = NULL,
                    status = CASE WHEN attempts + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END,
                    next_attempt_at = CASE
                        WHEN attempts + 1 >= ? THEN next_attempt_at
                        WHEN attempts + 1 = 1 THEN CURRENT_TIMESTAMP + INTERVAL '30 seconds'
                        ELSE CURRENT_TIMESTAMP + INTERVAL '5 minutes'
                    END
                WHERE id = ANY(?)
                """.trimIndent()
            ),
            parameterSetter = { statement, _ ->
                statement.setString(1, error.take(1000))
                statement.setInt(2, maxAttempts)
                statement.setInt(3, maxAttempts)
                statement.setArray(4, statement.connection.createArrayOf("bigint", ids.toTypedArray()))
            },
        ).process(Unit).logIfFailed("failOrReschedule(${ids.size})")
    }

    /** Reset a subscription's failure streak after a successful delivery. */
    suspend fun recordSubscriptionSuccess(subscriptionId: Int) {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.update(
                """
                UPDATE webhook_subscriptions
                SET consecutive_failures = 0, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND consecutive_failures <> 0
                """.trimIndent()
            ),
            parameterSetter = { statement, _ -> statement.setInt(1, subscriptionId) },
        ).process(Unit).logIfFailed("recordSubscriptionSuccess(sub=$subscriptionId)")
    }

    /**
     * Record a failed delivery against a subscription's health, auto-deactivating it once it has
     * [deactivateThreshold] consecutive failures.
     */
    suspend fun recordSubscriptionFailure(subscriptionId: Int, deactivateThreshold: Int) {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.update(
                """
                UPDATE webhook_subscriptions
                SET consecutive_failures = consecutive_failures + 1,
                    active = CASE WHEN consecutive_failures + 1 >= ? THEN false ELSE active END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """.trimIndent()
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, deactivateThreshold)
                statement.setInt(2, subscriptionId)
            },
        ).process(Unit).logIfFailed("recordSubscriptionFailure(sub=$subscriptionId)")
    }

    /**
     * Earliest `next_attempt_at` among PENDING rows whose subscription is still active, or `null`
     * if the outbox holds no such row. Drives [WebhookDeliveryPoller]'s timed wait: when this is
     * `null` the loop parks on the enqueue signal indefinitely (zero `webhook_deliveries` queries)
     * instead of polling on a fixed heartbeat.
     */
    suspend fun findNextScheduledDeliveryAt(): Instant? {
        val result = DatabaseSteps.query<Unit, Instant?>(
            sql = SafeSQL.select(
                """
                SELECT MIN(due) AS next_attempt_at FROM (
                    SELECT d.next_attempt_at AS due
                    FROM webhook_deliveries d
                    JOIN webhook_subscriptions s ON s.id = d.subscription_id
                    WHERE d.status = 'PENDING' AND s.active = true
                    UNION ALL
                    -- Claims expiring counts as scheduled work (MCO-357). Without this arm, a row
                    -- stranded IN_FLIGHT by a poller that died would not wake anything, and would
                    -- sit until the once-a-day cleanup happened past it — a 24 hour delay on a
                    -- webhook instead of a 5 minute one.
                    SELECT d.claimed_at + INTERVAL '$CLAIM_LEASE_MINUTES minutes' AS due
                    FROM webhook_deliveries d
                    JOIN webhook_subscriptions s ON s.id = d.subscription_id
                    WHERE d.status = 'IN_FLIGHT' AND d.claimed_at IS NOT NULL AND s.active = true
                ) scheduled
                """.trimIndent()
            ),
            parameterSetter = { _, _ -> },
            resultMapper = { rs -> if (rs.next()) rs.getTimestamp("next_attempt_at")?.toInstant() else null },
        ).process(Unit)
        return when (result) {
            is Result.Success -> result.value
            is Result.Failure -> {
                logger.error("Webhook DB op failed: {} ({})", "findNextScheduledDeliveryAt", result.error)
                null
            }
        }
    }

    /** Prune delivered rows older than 7 days and failed rows older than 30 days. */
    suspend fun pruneOldDeliveries() {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.delete(
                """
                DELETE FROM webhook_deliveries
                WHERE (status = 'DELIVERED' AND delivered_at < CURRENT_TIMESTAMP - INTERVAL '7 days')
                   OR (status = 'FAILED' AND updated_at < CURRENT_TIMESTAMP - INTERVAL '30 days')
                """.trimIndent()
            ),
            parameterSetter = { _, _ -> },
        ).process(Unit).logIfFailed("pruneOldDeliveries")
    }

    private fun parseFilter(raw: String?): List<String> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(stringListSerializer, raw) }.getOrElse {
            logger.warn("Unparseable event_filter '{}', treating as empty", raw)
            emptyList()
        }

    private fun <T> Result<*, List<T>>.orEmpty(op: String): List<T> = when (this) {
        is Result.Success -> value
        is Result.Failure -> {
            logger.error("Webhook DB op failed: {} ({})", op, error)
            emptyList()
        }
    }

    private fun Result<*, *>.logIfFailed(op: String) {
        if (this is Result.Failure) logger.error("Webhook DB op failed: {} ({})", op, error)
    }
}
