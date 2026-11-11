package app.mcorg.pipeline.minecraftfiles

import app.mcorg.config.AppConfig
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import org.slf4j.LoggerFactory

/**
 * Narrows the versions an ingestion run considers, from the `INGEST_VERSIONS` toggle.
 *
 * Production leaves it unset and ingests every supported release. It exists for environments that
 * start from an empty database every time — cloud sessions, a fresh local Docker DB — where a full
 * ingest of ~25 versions is minutes of downloads for a change that only needs one version's data.
 * Work that touches `mc-data` extraction across versions should still run without it.
 *
 * Runs *before* the ledger check, so it limits what is even looked at; `FORCE_REINGEST` still
 * applies to whatever survives.
 */
internal data object SelectIngestVersionsStep :
    Step<List<MinecraftVersion.Release>, AppFailure, List<MinecraftVersion.Release>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun process(input: List<MinecraftVersion.Release>): Result<AppFailure, List<MinecraftVersion.Release>> =
        select(input, AppConfig.ingestVersions).also { result ->
            if (result is Result.Success && result.value.size != input.size) {
                logger.info("INGEST_VERSIONS limits this run to ${result.value.joinToString()} (of ${input.size} available).")
            }
        }

    /**
     * - unset / blank / `all` → every available version (production behaviour);
     * - `latest` → the newest available release only;
     * - a comma-separated list (`1.21.8,26.2`) → exactly those. An entry that does not parse, or is
     *   not an available version (older than the earliest supported, or not in Mojang's manifest),
     *   fails the run rather than quietly ingesting less than was asked for.
     */
    internal fun select(
        available: List<MinecraftVersion.Release>,
        rawEnv: String?,
    ): Result<AppFailure, List<MinecraftVersion.Release>> {
        val raw = rawEnv?.trim().orEmpty()
        return when {
            raw.isEmpty() || raw.equals("all", ignoreCase = true) -> Result.success(available)
            raw.equals("latest", ignoreCase = true) -> Result.success(listOfNotNull(available.maxWithOrNull { a, b -> a.compareTo(b) }))
            else -> {
                val entries = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                val parsed = entries.associateWith { runCatching { MinecraftVersion.Release.fromString(it) }.getOrNull() }
                val unknown = parsed.filter { (_, version) -> version == null || version !in available }.keys
                if (unknown.isNotEmpty()) {
                    Result.failure(
                        AppFailure.IllegalConfigurationError(
                            "INGEST_VERSIONS names versions that are not available to ingest: ${unknown.joinToString()}"
                        )
                    )
                } else {
                    val wanted = parsed.values.filterNotNull().toSet()
                    Result.success(available.filter { it in wanted })
                }
            }
        }
    }
}
