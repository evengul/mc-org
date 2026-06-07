package app.mcorg.pipeline.minecraftfiles

import app.mcorg.config.MojangLauncherMetaApiConfig
import app.mcorg.data.minecraft.ExtractMinecraftDataStep
import app.mcorg.data.minecraft.extract.ExtractRelevantMinecraftFilesStep
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.pipelineResult
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.GetAvailableVersionsStep
import app.mcorg.pipeline.minecraft.StoreMinecraftDataStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.InputStream
import java.net.URI

suspend fun executeServerFilesPipeline(): Result<AppFailure, Unit> = pipelineResult {
    val versions = GetAvailableVersionsStep.run(Unit)
    val urls = GetServerUrlsStep.run(versions)
    val filtered = FilterAlreadyStoredVersionsStep.run(urls)
    ProcessServerFilesStep.run(filtered)
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

        return Result.success(
            input.filter { jar ->
                val ingest = shouldIngest(jar, byVersion[jar.version])
                if (ingest) {
                    logger.info("Version ${jar.version} will be downloaded and processed (ledger=${byVersion[jar.version]}, manifest sha=${jar.sha1}).")
                } else {
                    logger.info("Version ${jar.version} already ingested with matching server.jar SHA, skipping download.")
                }
                ingest
            }
        )
    }

    /**
     * Decide whether a resolved server.jar needs ingesting, given its current ledger entry (or null
     * if there is no row). Ingest when: there is no row (incl. post-truncate recreate), the previous
     * run did not complete, the stored SHA is unknown (backfilled NULL), or the SHA has changed.
     * Skip only when a completed row's stored SHA matches Mojang's advertised SHA (MCO-168).
     */
    internal fun shouldIngest(jar: ResolvedServerJar, entry: IngestionLedgerEntry?): Boolean = when {
        entry == null -> true
        entry.status != IngestionStatus.COMPLETED -> true
        entry.serverJarSha == null -> true
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
            val file = GetServerFileStep.run(version to jar.url)
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

data object GetServerFileStep : Step<Pair<MinecraftVersion.Release, URI>, AppFailure, Pair<MinecraftVersion.Release, InputStream>> {
    private val logger = LoggerFactory.getLogger(GetServerFileStep::class.java)
    override suspend fun process(input: Pair<MinecraftVersion.Release, URI>): Result<AppFailure, Pair<MinecraftVersion.Release, InputStream>> {
        return try {
            Result.success(input.first to withContext(Dispatchers.IO) {
                input.second.toURL().openStream()
            })
        } catch (e: Exception) {
            logger.error("Failed to download server file for version ${input.first} from ${input.second}: ${e.message}", e)
            Result.failure(AppFailure.ApiError.UnknownError)
        }
    }
}

/** Ledger status values, mirrored from the `minecraft_version_ingestion.status` CHECK constraint. */
object IngestionStatus {
    const val IN_PROGRESS = "in_progress"
    const val COMPLETED = "completed"
    const val FAILED = "failed"
}

/** A row of the ingestion ledger relevant to the freshness decision (status + last-ingested SHA). */
internal data class IngestionLedgerEntry(val status: String, val serverJarSha: String?)

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
            sql = SafeSQL.select("SELECT version, status, server_jar_sha FROM minecraft_version_ingestion"),
            resultMapper = { resultSet ->
                buildMap {
                    while (resultSet.next()) {
                        val versionString = resultSet.getString("version")
                        try {
                            put(
                                MinecraftVersion.Release.fromString(versionString),
                                IngestionLedgerEntry(resultSet.getString("status"), resultSet.getString("server_jar_sha")),
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
                    server_jar_sha = ?, server_jar_url = ?
                WHERE version = ?
            """.trimIndent()),
            parameterSetter = { statement, jar ->
                statement.setString(1, jar.sha1)
                statement.setString(2, jar.url.toString())
                statement.setString(3, jar.version.toString())
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
