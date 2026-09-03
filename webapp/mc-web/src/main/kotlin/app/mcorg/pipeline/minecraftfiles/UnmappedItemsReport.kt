package app.mcorg.pipeline.minecraftfiles

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Step
import app.mcorg.item.ItemGlyph
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * The glyph gaps recorded for one ingested Minecraft version (MCO-475).
 *
 * [itemIds] are bare Minecraft registry names — no namespace, already sorted, technical ids already
 * excluded by [ItemGlyph.unmapped] at write time.
 */
internal data class UnmappedItemsReport(
    val version: MinecraftVersion.Release,
    val itemIds: List<String>,
)

/**
 * The newest completed version's `unmapped_items`, or null when nothing has been ingested.
 *
 * "Newest" is decided in Kotlin rather than by `ORDER BY version`: the column is a `VARCHAR`, and
 * lexical order puts `1.9.0` above `1.21.4`. Rows whose version string does not parse are dropped
 * rather than failing the read — this is a warning path, and one malformed ledger row should not
 * cost the report for every other version.
 */
internal data object LoadNewestUnmappedItemsStep : Step<Unit, AppFailure.DatabaseError, UnmappedItemsReport?> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, UnmappedItemsReport?> =
        DatabaseSteps.query<Unit, UnmappedItemsReport?>(
            sql = SafeSQL.select(
                "SELECT version, unmapped_items FROM minecraft_version_ingestion WHERE status = 'completed'"
            ),
            resultMapper = { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        val version = try {
                            MinecraftVersion.Release.fromString(resultSet.getString("version"))
                        } catch (_: IllegalArgumentException) {
                            continue
                        }
                        val items = (resultSet.getArray("unmapped_items")?.array as? Array<*>)
                            ?.filterIsInstance<String>()
                            .orEmpty()
                        add(UnmappedItemsReport(version, items))
                    }
                }.maxWithOrNull(
                    compareBy({ it.version.major }, { it.version.minor }, { it.version.patch })
                )
            },
        ).process(input)
}

private val logger = LoggerFactory.getLogger("app.mcorg.pipeline.minecraftfiles.UnmappedItemsReport")

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("seam-glyph-gap-report"))

/**
 * **Interim (MCO-475). Delete this when the log drain and its monitor exist (MCO-343).**
 *
 * The gap is discovered by the nightly ingest, which runs on a short-lived Fly machine whose logs
 * are retained for roughly half an hour and are not yet shipped anywhere. A warning emitted there is
 * written to a channel nobody reads that then deletes itself, so the ledger column is the durable
 * record and this is the one place likely to actually be *seen*: the web app is long-lived and
 * restarts on every deploy, so the line reappears until someone acts on it.
 *
 * One indexed read of a table with a handful of rows, once, off the startup path — `ApplicationStarted`
 * fires after the server is up, and the query runs on its own scope so a slow or cold database never
 * delays serving traffic. Silent when there is nothing to say.
 *
 * Item ids are Minecraft registry names — public game data, not user-authored content — so naming
 * them in full sits inside documentation/logging.md's pseudonymous posture.
 */
fun Application.configureUnmappedItemWarning() {
    monitor.subscribe(ApplicationStarted) {
        scope.launch {
            when (val result = LoadNewestUnmappedItemsStep.process(Unit)) {
                is Result.Failure ->
                    logger.warn("Could not read the item-glyph gap report from the ingestion ledger: {}", result.error)
                is Result.Success -> {
                    val report = result.value
                    if (report != null && report.itemIds.isNotEmpty()) {
                        logger.warn(
                            "Minecraft {} has {} item(s) with no Seam glyph; they render as the unmapped " +
                                "mark until a rule is added to ItemGlyph: {}",
                            report.version,
                            report.itemIds.size,
                            report.itemIds.joinToString(", "),
                        )
                    }
                }
            }
        }
    }
}
