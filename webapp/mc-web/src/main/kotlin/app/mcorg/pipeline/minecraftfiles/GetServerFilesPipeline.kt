package app.mcorg.pipeline.minecraftfiles

import app.mcorg.config.AppConfig
import app.mcorg.config.Database
import app.mcorg.config.MojangLauncherMetaApiConfig
import app.mcorg.data.minecraft.ExtractMinecraftDataStep
import app.mcorg.data.minecraft.ExtractionVersion
import app.mcorg.data.minecraft.extract.ExtractRelevantMinecraftFilesStep
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.pipelineResult
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.TransactionConnection
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.GetAvailableVersionsStep
import app.mcorg.pipeline.minecraft.StoreMinecraftDataStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * Fixed advisory-lock key guarding a whole ingestion run. Any stable bigint works; this value is
 * arbitrary and shared by every invocation path (app boot, manual run, the future cron machine) so
 * they serialise against each other.
 */
private const val INGESTION_ADVISORY_LOCK_KEY = 7331L

/*
 * Timeouts for the Mojang server.jar download (MCO-346).
 *
 * This is the one outbound call in the codebase that does not go through ApiProvider and its 30s
 * HttpTimeout, and JDK URL streams default to no timeout at all. Because the download runs inside
 * the ingestion advisory lock, a stalled CDN connection did not merely fail one run — it parked
 * the Fly machine forever, and every later run then found the lock held and logged "another
 * ingestion run is in progress, skipping". An indefinite outage that reads as success.
 *
 * The three bounds are layered: connect covers a dead endpoint, read covers a connection that
 * goes quiet mid-transfer, and the wall clock covers one that dribbles bytes indefinitely without
 * ever tripping the read timeout. Generous, because a server.jar is ~50 MB and the machine has no
 * competing work — the point is a ceiling, not a tight SLA.
 */
internal data class DownloadTimeouts(
    val connectMs: Int = 30_000,
    val readMs: Int = 60_000,
    val wallClockMs: Long = 10L * 60 * 1000,
)

private val ingestionLogger = LoggerFactory.getLogger("app.mcorg.pipeline.minecraftfiles.ServerFilesIngestion")

/**
 * Runs server-files ingestion under a Postgres advisory lock so overlapping triggers become silent
 * no-ops instead of duplicate work. The lock is held on a dedicated connection for the whole run and
 * released on every exit path, including failure and cancellation (MCO-169).
 */
suspend fun executeServerFilesPipeline(): Result<AppFailure, Unit> = withIngestionLock {
    pipelineResult {
        val versions = GetAvailableVersionsStep.run(Unit)
        val urls = GetServerUrlsStep.run(versions)
        val filtered = FilterAlreadyStoredVersionsStep.run(urls)
        ProcessServerFilesStep.run(filtered)
    }
}

/** Whether the lock was taken, and which PostgreSQL backend answered the question. */
internal data class LockAttempt(val acquired: Boolean, val backendPid: Int)

/**
 * Acquires the ingestion advisory lock on a dedicated connection and runs [block]. If another run
 * already holds the lock, logs and returns success without running [block] — overlapping triggers
 * become no-ops. The lock is released and the connection returned on every path; release runs under
 * [NonCancellable] so a cancelled run still frees the lock rather than leaking it on the pooled
 * session (a pg advisory lock is session-scoped and Hikari keeps sessions alive across checkouts).
 *
 * Both acquire and release record `pg_backend_pid()` (MCO-348). A pg advisory lock belongs to a
 * *session*, and production connects through Neon's pooler, so the open question is whether the
 * backend can change underneath a lock we believe we are holding. Comparing the two PIDs answers
 * that directly for any run: equal means one backend held it throughout, different is the failure
 * mode itself, caught in the act.
 *
 * Logged at INFO on the happy path deliberately. Before this the success path was silent, so a
 * run's logs could only ever say "the warning did not appear" — and with Fly retaining roughly
 * half an hour and no drain yet (MCO-343), absence of a warning in a window that short is very
 * weak evidence. Two positive lines make a single run self-evidencing.
 */
internal suspend fun withIngestionLock(block: suspend () -> Result<AppFailure, Unit>): Result<AppFailure, Unit> {
    val connection = try {
        TransactionConnection(withContext(Dispatchers.IO) { Database.getConnection() })
    } catch (e: Exception) {
        ingestionLogger.error("Could not open a connection for the ingestion advisory lock", e)
        return Result.failure(AppFailure.DatabaseError.ConnectionError)
    }

    return connection.connection.use {
        when (val attempt = tryAdvisoryLock(connection)) {
            is Result.Failure -> attempt
            is Result.Success -> if (!attempt.value.acquired) {
                reportContention(connection)
            } else {
                val acquiredPid = attempt.value.backendPid
                val startedAt = System.nanoTime()
                ingestionLogger.info(
                    "Acquired ingestion advisory lock {} on backend pid {}.",
                    INGESTION_ADVISORY_LOCK_KEY,
                    acquiredPid,
                )
                try {
                    block()
                } finally {
                    withContext(NonCancellable) {
                        releaseAdvisoryLock(connection, acquiredPid, startedAt)
                    }
                }
            }
        }
    }
}

/**
 * How long a held lock may plausibly represent a live run before it is treated as stuck.
 *
 * Deliberately longer than the CLI's 45 minute wall clock: past that ceiling a healthy run has
 * already killed itself, so a lock still held is not "another run in progress".
 */
private val STUCK_LOCK_AFTER_MINUTES = 60

/**
 * Reports a lock we could not take, and decides whether that is routine or a fault.
 *
 * The previous version of this logged `pg_backend_pid()` from the *acquire* query — which is this
 * process's own backend, by definition not the one holding the lock. It printed a different
 * meaningless number every run, so an operator reading "lock 7331 held; asked backend pid 4711"
 * had nothing to act on. The holder's pid comes from `pg_locks`, which is the only place it exists.
 *
 * The distinction this draws matters more than the pid. Skipping because a legitimate run is in
 * progress is a success; skipping because a lock has been stranded — the exact failure MCO-346's
 * post-mortem describes, where every nightly run afterwards logged "in progress" and exited 0 — is
 * not, and until now the two were indistinguishable from the outside. A lock older than
 * [STUCK_LOCK_AFTER_MINUTES] fails the run so the scheduled machine's exit code shows it.
 */
private suspend fun reportContention(connection: TransactionConnection): Result<AppFailure, Unit> {
    val holder = DatabaseSteps.query<Long, LockHolder?>(
        // classid/objid are the two halves of the 64-bit advisory key; objsubid = 1 marks the
        // single-argument pg_advisory_lock(bigint) form.
        sql = SafeSQL.select(
            """
            SELECT l.pid,
                   EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - a.xact_start))::bigint AS xact_age_s,
                   EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - a.backend_start))::bigint AS backend_age_s,
                   a.state
            FROM pg_locks l
            LEFT JOIN pg_stat_activity a ON a.pid = l.pid
            WHERE l.locktype = 'advisory'
              AND l.granted
              AND ((l.classid::bigint << 32) | l.objid::bigint) = ?
            LIMIT 1
            """.trimIndent()
        ),
        parameterSetter = { statement, key -> statement.setLong(1, key) },
        resultMapper = { rs ->
            if (rs.next()) {
                LockHolder(
                    pid = rs.getInt("pid"),
                    ageSeconds = rs.getLong("backend_age_s"),
                    state = rs.getString("state") ?: "unknown",
                )
            } else {
                null
            }
        },
        transactionConnection = connection,
    ).process(INGESTION_ADVISORY_LOCK_KEY).getOrNull()

    val ageMinutes = (holder?.ageSeconds ?: 0) / 60
    return when {
        holder == null -> {
            // The lock was taken between our attempt and this query, or is held on a backend this
            // session cannot see. Routine enough not to fail the run, odd enough to say so.
            ingestionLogger.info(
                "Another ingestion run is in progress, skipping (lock {} held; holder not visible in pg_locks).",
                INGESTION_ADVISORY_LOCK_KEY,
            )
            Result.success()
        }

        ageMinutes >= STUCK_LOCK_AFTER_MINUTES -> {
            ingestionLogger.error(
                "Ingestion advisory lock {} looks stuck: held by backend pid {} (state {}) for {} minutes, " +
                    "past the {} minute wall clock a healthy run cannot exceed. No ingestion has run since. " +
                    "Recover with: SELECT pg_terminate_backend({}).",
                INGESTION_ADVISORY_LOCK_KEY, holder.pid, holder.state, ageMinutes,
                STUCK_LOCK_AFTER_MINUTES, holder.pid,
            )
            Result.failure(AppFailure.DatabaseError.UnknownError)
        }

        else -> {
            ingestionLogger.info(
                "Another ingestion run is in progress, skipping (lock {} held by backend pid {}, state {}, for {} minutes).",
                INGESTION_ADVISORY_LOCK_KEY, holder.pid, holder.state, ageMinutes,
            )
            Result.success()
        }
    }
}

private data class LockHolder(val pid: Int, val ageSeconds: Long, val state: String)

private suspend fun tryAdvisoryLock(connection: TransactionConnection): Result<AppFailure.DatabaseError, LockAttempt> =
    DatabaseSteps.query<Long, LockAttempt>(
        sql = SafeSQL.select("SELECT pg_try_advisory_lock(?), pg_backend_pid()"),
        parameterSetter = { statement, key -> statement.setLong(1, key) },
        resultMapper = { rs ->
            if (rs.next()) LockAttempt(rs.getBoolean(1), rs.getInt(2)) else LockAttempt(false, -1)
        },
        transactionConnection = connection,
    ).process(INGESTION_ADVISORY_LOCK_KEY)

private suspend fun releaseAdvisoryLock(
    connection: TransactionConnection,
    acquiredPid: Int,
    startedAt: Long,
) {
    val heldMs = (System.nanoTime() - startedAt) / 1_000_000
    DatabaseSteps.query<Long, LockAttempt>(
        sql = SafeSQL.select("SELECT pg_advisory_unlock(?), pg_backend_pid()"),
        parameterSetter = { statement, key -> statement.setLong(1, key) },
        resultMapper = { rs ->
            if (rs.next()) LockAttempt(rs.getBoolean(1), rs.getInt(2)) else LockAttempt(false, -1)
        },
        transactionConnection = connection,
    ).process(INGESTION_ADVISORY_LOCK_KEY).fold(
        onSuccess = { release ->
            when {
                !release.acquired -> ingestionLogger.warn(
                    "Ingestion advisory lock {} was not held at release time (acquired on backend pid {}, released from {}, held {} ms).",
                    INGESTION_ADVISORY_LOCK_KEY, acquiredPid, release.backendPid, heldMs,
                )
                // Released fine, but from a different backend than acquired it. The unlock
                // succeeding here would be surprising, so treat the mismatch itself as the
                // finding — it is the exact mechanism MCO-348 hypothesises.
                release.backendPid != acquiredPid -> ingestionLogger.warn(
                    "Ingestion advisory lock {} changed backend mid-run: acquired on pid {}, released from pid {} after {} ms. The pooler swapped the session underneath a session-scoped lock (MCO-348).",
                    INGESTION_ADVISORY_LOCK_KEY, acquiredPid, release.backendPid, heldMs,
                )
                else -> ingestionLogger.info(
                    "Released ingestion advisory lock {} on backend pid {} after {} ms.",
                    INGESTION_ADVISORY_LOCK_KEY, acquiredPid, heldMs,
                )
            }
        },
        onFailure = { ingestionLogger.error("Failed to release ingestion advisory lock: $it") },
    )
}


/** A Minecraft release resolved to its server.jar download URL and the SHA1 Mojang advertises for it. */
data class ResolvedServerJar(
    val version: MinecraftVersion.Release,
    val url: URI,
    val sha1: String,
)

data object GetServerUrlsStep : Step<List<MinecraftVersion.Release>, AppFailure, List<ResolvedServerJar>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun process(input: List<MinecraftVersion.Release>): Result<AppFailure, List<ResolvedServerJar>> = pipelineResult {
        val provider = MojangLauncherMetaApiConfig.getProvider()

        val manifest = provider.get<Unit, VersionManifest>(
            url = MojangLauncherMetaApiConfig.getVersionManifestUrl(),
        ).run(Unit)

        // Index manifest entries by parsed Release for O(1) lookup
        val entryByVersion: Map<MinecraftVersion.Release, VersionManifest.ManifestEntry> =
            manifest.versions
                .filter { it.type == "release" }
                .mapNotNull { entry ->
                    runCatching { MinecraftVersion.Release.fromString(entry.id) }.getOrNull()
                        ?.let { it to entry }
                }
                .toMap()

        input.mapNotNull { version ->
            val entry = entryByVersion[version]
            if (entry == null) {
                logger.warn("Version $version not found in Mojang manifest, skipping")
                return@mapNotNull null
            }
            val server = provider.get<Unit, VersionMeta>(url = entry.url).process(Unit).getOrNull()
                ?.downloads?.server
            if (server == null) {
                logger.warn("No server.jar download for $version in Mojang metadata, skipping")
                return@mapNotNull null
            }
            ResolvedServerJar(version, URI.create(server.url), server.sha1)
        }
    }
}

internal data object FilterAlreadyStoredVersionsStep : Step<List<ResolvedServerJar>, AppFailure, List<ResolvedServerJar>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun process(input: List<ResolvedServerJar>): Result<AppFailure, List<ResolvedServerJar>> {
        val ledger = LoadIngestionStatusStep.process(Unit)
        if (ledger is Result.Failure) {
            logger.error("Failed to load ingestion status from the ledger.")
            return ledger
        }
        val byVersion = ledger.getOrNull().orEmpty()
        val forced = forcedPredicate(AppConfig.forceReingest)

        return Result.success(
            input.filter { jar ->
                val force = forced(jar.version)
                val ingest = force || shouldIngest(jar, byVersion[jar.version])
                when {
                    force -> logger.info("Version ${jar.version} forced for re-ingest via FORCE_REINGEST (storage is idempotent: it deletes and re-inserts this version's sources).")
                    ingest -> logger.info("Version ${jar.version} will be downloaded and processed (ledger=${byVersion[jar.version]}, manifest sha=${jar.sha1}).")
                    else -> logger.info("Version ${jar.version} already ingested, skipping download.")
                }
                ingest
            }
        )
    }

    /**
     * Parses the `FORCE_REINGEST` toggle into a per-version predicate. The freshness check
     * skips a version whose stored SHA still matches Mojang's, which is correct in production
     * but blocks re-running an unchanged version after an *extraction-code* change (new
     * synthetic sources, parser fixes). Setting `FORCE_REINGEST` overrides the skip — re-ingest
     * is safe because [app.mcorg.pipeline.minecraft.StoreMinecraftDataStep] deletes and
     * re-inserts a version's sources rather than appending.
     *
     * - unset / `false` → force nothing (normal freshness behaviour);
     * - `true` / `all` → force every resolved version;
     * - a comma-separated version list (e.g. `26.2.0,1.21.8`) → force just those.
     */
    internal fun forcedPredicate(rawEnv: String?): (MinecraftVersion.Release) -> Boolean {
        val raw = rawEnv?.trim().orEmpty()
        return when {
            raw.isEmpty() || raw.equals("false", ignoreCase = true) -> { _ -> false }
            raw.equals("true", ignoreCase = true) || raw.equals("all", ignoreCase = true) -> { _ -> true }
            else -> {
                val versions = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                ({ version -> version.toString() in versions })
            }
        }
    }

    /**
     * Decide whether a resolved server.jar needs ingesting, given its current ledger entry (or null
     * if there is no row). Ingest when: there is no row (incl. post-truncate recreate), the previous
     * run did not complete, the **extraction code changed** since this version was ingested (stored
     * extraction_version older than [ExtractionVersion.CURRENT]), or the SHA has changed. A completed,
     * extraction-current row whose stored SHA is unknown (NULL) is NOT re-ingested — re-ingesting
     * would duplicate resource_source rows, and the existing data already came from this immutable
     * release jar. The pre-ledger versions had their SHAs backfilled by migration `V2_36_0`; any NULL
     * that slips through (environment drift) is simply skipped. Skip when a completed row is on the
     * current extraction version and its stored SHA matches Mojang's advertised SHA (MCO-168).
     */
    internal fun shouldIngest(jar: ResolvedServerJar, entry: IngestionLedgerEntry?): Boolean = when {
        entry == null -> true
        entry.status != IngestionStatus.COMPLETED -> true
        entry.extractionVersion < ExtractionVersion.CURRENT -> true
        entry.serverJarSha == null -> false
        else -> entry.serverJarSha != jar.sha1
    }
}

private data object ProcessServerFilesStep : Step<List<ResolvedServerJar>, AppFailure, Unit> {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    override suspend fun process(input: List<ResolvedServerJar>): Result<AppFailure, Unit> {
        if (input.isEmpty()) {
            logger.info("No new server files to process.")
            return Result.success()
        }

        val result = input.map { jar ->
            MDC.put("minecraftVersion", jar.version.toString())
            val stepResult = processServerFile(jar)
            try {
                delay(500)
            } catch (e: Exception) {
                logger.warn("Delay interrupted: ${e.message}", e)
            }
            stepResult
        }

        val errors = result.filterIsInstance<Result.Failure<AppFailure>>().map { it.error }.distinctBy { it.javaClass }

        if (errors.isNotEmpty()) {
            return Result.failure(errors.first())
        }

        return Result.success()
    }

    private suspend fun processServerFile(jar: ResolvedServerJar): Result<AppFailure, Unit> {
        val version = jar.version

        val marked = MarkIngestionInProgressStep.process(version)
        if (marked is Result.Failure) {
            logger.error("Could not mark ingestion in progress for version $version, skipping.")
            return marked
        }

        val result: Result<AppFailure, Unit> = pipelineResult {
            val file = GetServerFileStep.run(jar)
            val extracted = ExtractRelevantMinecraftFilesStep().process(file)
                .mapError { AppFailure.FileError(ProcessServerFilesStep.javaClass) }
                .bind()
            val data = ExtractMinecraftDataStep.process(extracted)
                .mapError { AppFailure.FileError(ProcessServerFilesStep.javaClass) }
                .bind()
            StoreMinecraftDataStep.run(data)
        }

        return when (result) {
            is Result.Success -> MarkIngestionCompletedStep.process(jar)
            is Result.Failure -> {
                // Best-effort: record the failure so a rerun retries this version only. If the ledger
                // write itself fails we still surface the original, more informative error.
                MarkIngestionFailedStep.process(version to result.error.toString())
                result
            }
        }
    }
}

/**
 * Downloads a server.jar to a temp file, verifying it against the SHA1 Mojang advertises in the
 * version metadata. The returned stream opens the verified file with DELETE_ON_CLOSE, so the
 * consumer closing it (ExtractRelevantMinecraftFilesStep's `use`) also removes the temp file.
 */
data object GetServerFileStep : Step<ResolvedServerJar, AppFailure, Pair<MinecraftVersion.Release, InputStream>> {
    private val logger = LoggerFactory.getLogger(GetServerFileStep::class.java)

    override suspend fun process(input: ResolvedServerJar): Result<AppFailure, Pair<MinecraftVersion.Release, InputStream>> =
        download(input, DownloadTimeouts())

    /**
     * Timeouts are a parameter rather than a constant so the timeout branches are testable in
     * milliseconds instead of minutes. Production always takes the defaults.
     */
    internal suspend fun download(
        input: ResolvedServerJar,
        timeouts: DownloadTimeouts,
    ): Result<AppFailure, Pair<MinecraftVersion.Release, InputStream>> {
        return try {
            withTimeout(timeouts.wallClockMs) {
                withContext(Dispatchers.IO) {
                    val tempFile = Files.createTempFile("server-${input.version}", ".jar")
                    try {
                        val digest = MessageDigest.getInstance("SHA-1")
                        // openConnection() rather than openStream(), purely to reach the timeout
                        // setters — openStream() is openConnection().getInputStream() with both
                        // left at their default of 0, meaning infinite (MCO-346).
                        val connection = input.url.toURL().openConnection().apply {
                            connectTimeout = timeouts.connectMs
                            readTimeout = timeouts.readMs
                        }
                        connection.getInputStream().use { remote ->
                            DigestInputStream(remote, digest).use { digested ->
                                copyCancellably(digested, tempFile)
                            }
                        }
                        val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                        if (!actualSha.equals(input.sha1, ignoreCase = true)) {
                            logger.error("SHA-1 mismatch for ${input.version} server.jar from ${input.url}: expected ${input.sha1}, got $actualSha")
                            Files.deleteIfExists(tempFile)
                            Result.failure(AppFailure.ApiError.ChecksumMismatch(expected = input.sha1, actual = actualSha))
                        } else {
                            Result.success(input.version to Files.newInputStream(tempFile, StandardOpenOption.DELETE_ON_CLOSE))
                        }
                    } catch (e: Exception) {
                        Files.deleteIfExists(tempFile)
                        throw e
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            // Deliberately caught rather than propagated. This coroutine's cancellation must not
            // escape into withIngestionLock, which needs to unwind normally so the advisory lock
            // is released — an ingest that dies holding lock 7331 makes every subsequent nightly
            // run a silent no-op.
            logger.error(
                "Timed out after {} ms downloading the {} server.jar; abandoning this version",
                timeouts.wallClockMs,
                input.version,
            )
            Result.failure(AppFailure.ApiError.TimeoutError)
        } catch (e: SocketTimeoutException) {
            logger.error("Connection to Mojang stalled while downloading the ${input.version} server.jar: ${e.message}")
            Result.failure(AppFailure.ApiError.TimeoutError)
        } catch (e: Exception) {
            logger.error("Failed to download server file for version ${input.version} from ${input.url}: ${e.message}", e)
            Result.failure(AppFailure.ApiError.UnknownError)
        }
    }

    /**
     * Copies [source] to [target] a chunk at a time, checking for cancellation between chunks.
     *
     * `Files.copy` would be shorter but loops entirely inside one uninterruptible call, so the
     * enclosing [withTimeout] could not fire until the whole transfer finished. The socket-level
     * read timeout covers a connection that stops sending; this covers one that dribbles bytes
     * slowly enough to stay under that timeout forever.
     */
    private suspend fun copyCancellably(source: InputStream, target: java.nio.file.Path) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { out ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = source.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
            }
        }
    }
}

/** Ledger status values, mirrored from the `minecraft_version_ingestion.status` CHECK constraint. */
object IngestionStatus {
    const val IN_PROGRESS = "in_progress"
    const val COMPLETED = "completed"
    const val FAILED = "failed"
}

/**
 * A row of the ingestion ledger relevant to the freshness decision: status, last-ingested SHA,
 * and the extraction-code version it ran under. [extractionVersion] defaults to the current code
 * version (used only by tests focused on the SHA path); [LoadIngestionStatusStep] always reads the
 * real stored value.
 */
internal data class IngestionLedgerEntry(
    val status: String,
    val serverJarSha: String?,
    val extractionVersion: Int = ExtractionVersion.CURRENT,
)

/**
 * Reads the ingestion ledger into a `version -> entry` map. A version absent from the map has no
 * ledger row and is treated as "never ingested" by the caller. Replaces the legacy 4-table EXISTS
 * proxy as the source of truth for ingestion decisions (MCO-167); carries the stored server.jar SHA
 * for the freshness check (MCO-168).
 */
internal data object LoadIngestionStatusStep : Step<Unit, AppFailure.DatabaseError, Map<MinecraftVersion.Release, IngestionLedgerEntry>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, Map<MinecraftVersion.Release, IngestionLedgerEntry>> =
        DatabaseSteps.query<Unit, Map<MinecraftVersion.Release, IngestionLedgerEntry>>(
            sql = SafeSQL.select("SELECT version, status, server_jar_sha, extraction_version FROM minecraft_version_ingestion"),
            resultMapper = { resultSet ->
                buildMap {
                    while (resultSet.next()) {
                        val versionString = resultSet.getString("version")
                        try {
                            put(
                                MinecraftVersion.Release.fromString(versionString),
                                IngestionLedgerEntry(
                                    resultSet.getString("status"),
                                    resultSet.getString("server_jar_sha"),
                                    resultSet.getInt("extraction_version"),
                                ),
                            )
                        } catch (e: IllegalArgumentException) {
                            logger.error("Invalid version format in ingestion ledger: $versionString", e)
                        }
                    }
                }
            }
        ).process(Unit)
}

/**
 * Marks a version's ingestion as started: upserts an `in_progress` row, stamps `started_at`,
 * bumps `attempt_count`, and clears any stale `last_error`. No FK to minecraft_version, so this is
 * safe for brand-new versions whose data does not exist yet (MCO-167).
 */
internal data object MarkIngestionInProgressStep : Step<MinecraftVersion.Release, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: MinecraftVersion.Release): Result<AppFailure.DatabaseError, Unit> =
        DatabaseSteps.update<MinecraftVersion.Release>(
            sql = SafeSQL.insert("""
                INSERT INTO minecraft_version_ingestion (version, status, started_at, attempt_count)
                VALUES (?, 'in_progress', now(), 1)
                ON CONFLICT (version) DO UPDATE SET
                    status = 'in_progress',
                    started_at = now(),
                    attempt_count = minecraft_version_ingestion.attempt_count + 1,
                    last_error = NULL
            """.trimIndent()),
            parameterSetter = { statement, version -> statement.setString(1, version.toString()) }
        ).process(input).map { }
}

/**
 * Marks a version's ingestion as completed, records the server.jar SHA + URL it was ingested from,
 * and clears any prior error. The stored SHA is what the next run's freshness check compares against
 * (MCO-167 set status; MCO-168 adds the SHA/URL).
 */
internal data object MarkIngestionCompletedStep : Step<ResolvedServerJar, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: ResolvedServerJar): Result<AppFailure.DatabaseError, Unit> =
        DatabaseSteps.update<ResolvedServerJar>(
            sql = SafeSQL.update("""
                UPDATE minecraft_version_ingestion
                SET status = 'completed', completed_at = now(), last_error = NULL,
                    server_jar_sha = ?, server_jar_url = ?, extraction_version = ?
                WHERE version = ?
            """.trimIndent()),
            parameterSetter = { statement, jar ->
                statement.setString(1, jar.sha1)
                statement.setString(2, jar.url.toString())
                statement.setInt(3, ExtractionVersion.CURRENT)
                statement.setString(4, jar.version.toString())
            }
        ).process(input).map { }
}

/** Records an ingestion failure with its error message, leaving completed_at untouched (MCO-167). */
internal data object MarkIngestionFailedStep : Step<Pair<MinecraftVersion.Release, String>, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: Pair<MinecraftVersion.Release, String>): Result<AppFailure.DatabaseError, Unit> =
        DatabaseSteps.update<Pair<MinecraftVersion.Release, String>>(
            sql = SafeSQL.update("""
                UPDATE minecraft_version_ingestion
                SET status = 'failed', last_error = ?
                WHERE version = ?
            """.trimIndent()),
            parameterSetter = { statement, (version, error) ->
                statement.setString(1, error)
                statement.setString(2, version.toString())
            }
        ).process(input).map { }
}
