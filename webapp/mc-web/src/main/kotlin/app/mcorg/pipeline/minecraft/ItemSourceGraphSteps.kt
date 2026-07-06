package app.mcorg.pipeline.minecraft

import app.mcorg.config.CacheManager
import app.mcorg.config.CachedItemSourceGraph
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.services.ItemSourceGraphBuilder
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Runtime seam between the persisted Minecraft data and the pure planning
 * engine: loads a version's `resource_source_*` rows back into [ResourceSource]s
 * and builds the [ItemSourceGraph] the planner runs on, cached per version.
 *
 * Ingestion runs in a separate Fly machine (MCO-171), so [CacheManager.onVersionIngested]
 * fired from that JVM can never invalidate *this* JVM's cache — a re-ingest of an
 * already-cached version would otherwise be served stale until the web app restarts. To
 * self-invalidate, every cache hit is checked against `minecraft_version_ingestion.completed_at`
 * (MCO-252): if the DB reports a completion strictly after the cached build, the entry is
 * treated as a miss and rebuilt. [LoadVersionIngestionEpochStep] bounds that check to a single
 * indexed SELECT, further capped by [CacheManager.versionIngestionEpoch]'s short TTL.
 */
object GetItemSourceGraphForVersionStep : Step<String, AppFailure, ItemSourceGraph> {

    override suspend fun process(input: String): Result<AppFailure, ItemSourceGraph> {
        CacheManager.itemSourceGraph.getIfPresent(input)?.let { cached ->
            if (!isStale(cached.builtAt, currentEpoch(input))) return Result.success(cached.graph)
        }
        return LoadResourceSourcesForVersionStep.process(input).map { sources ->
            ItemSourceGraphBuilder.buildFromResourceSources(sources)
                .let { CachedItemSourceGraph(it, Instant.now()) }
                .also { CacheManager.itemSourceGraph.put(input, it) }
                .graph
        }
    }

    /**
     * A cached graph is stale once the DB reports a completion strictly after it was built.
     * A `null` epoch (no ledger row yet, or the lookup failed) means "no fresher signal
     * available" — not stale — so ledger-less versions (as in some test fixtures) still cache
     * normally instead of rebuilding on every access.
     */
    internal fun isStale(builtAt: Instant, dbEpoch: Instant?): Boolean =
        dbEpoch != null && dbEpoch.isAfter(builtAt)

    /** The version's ingestion epoch, from the short-TTL cache when present, else one SELECT. */
    internal suspend fun currentEpoch(version: String): Instant? {
        CacheManager.versionIngestionEpoch.getIfPresent(version)?.let { return it }
        return LoadVersionIngestionEpochStep.process(version).getOrNull()
            ?.also { CacheManager.versionIngestionEpoch.put(version, it) }
    }
}

/**
 * Reads a version's ingestion-completion epoch (`completed_at`) straight from the ledger. Only
 * `completed`-status rows count — an in-progress or failed re-ingest must not be treated as a
 * fresher signal than what's already cached. Absent row, non-completed status, or a `NULL`
 * `completed_at` all yield `null` (see [GetItemSourceGraphForVersionStep.isStale]).
 */
internal object LoadVersionIngestionEpochStep : Step<String, AppFailure.DatabaseError, Instant?> {

    private val query = DatabaseSteps.query<String, Instant?>(
        sql = SafeSQL.select(
            "SELECT completed_at FROM minecraft_version_ingestion WHERE version = ? AND status = 'completed'"
        ),
        parameterSetter = { ps, version -> ps.setString(1, version) },
        resultMapper = { rs -> if (rs.next()) rs.getTimestamp("completed_at")?.toInstant() else null }
    )

    override suspend fun process(input: String): Result<AppFailure.DatabaseError, Instant?> = query.process(input)
}

/**
 * Reassembles a version's [ResourceSource] list from the persisted tables —
 * the inverse of ingestion's StoreResourceSourcesStep. Item display names come
 * from `minecraft_items`; tag members from `minecraft_tag_item`. Fails with
 * NotFound when the version has no sources (not ingested).
 */
object LoadResourceSourcesForVersionStep : Step<String, AppFailure.DatabaseError, List<ResourceSource>> {

    private val logger = LoggerFactory.getLogger(javaClass)

    private data class SourceRow(val id: Int, val typeId: String, val filename: String)
    private data class EdgeRow(
        val sourceId: Int,
        val kind: String,
        val ref: String,
        val count: Int,
        val name: String?,
        val expectedYield: Double?
    )

    private val tagsQuery = DatabaseSteps.query<String, Map<String, MinecraftTag>>(
        sql = SafeSQL.select(
            """
            SELECT t.tag, t.name AS tag_name, ti.item, mi.item_name
            FROM minecraft_tag t
            LEFT JOIN minecraft_tag_item ti ON ti.version = t.version AND ti.tag = t.tag
            LEFT JOIN minecraft_items mi ON mi.version = t.version AND mi.item_id = ti.item
            WHERE t.version = ?
            ORDER BY t.tag, ti.item
            """.trimIndent()
        ),
        parameterSetter = { ps, version -> ps.setString(1, version) },
        resultMapper = { rs ->
            val names = mutableMapOf<String, String>()
            val members = mutableMapOf<String, MutableList<Item>>()
            while (rs.next()) {
                val tag = rs.getString("tag")
                names[tag] = rs.getString("tag_name")
                val memberId: String? = rs.getString("item")
                if (memberId != null) {
                    val memberName = rs.getString("item_name") ?: memberId.substringAfterLast(':')
                    members.getOrPut(tag) { mutableListOf() }.add(Item(memberId, memberName))
                }
            }
            names.mapValues { (tag, name) -> MinecraftTag(tag, name, members[tag].orEmpty()) }
        }
    )

    private val sourcesQuery = DatabaseSteps.query<String, List<SourceRow>>(
        sql = SafeSQL.select(
            """
            SELECT id, source_type, created_from_filename
            FROM resource_source
            WHERE version = ?
            ORDER BY id
            """.trimIndent()
        ),
        parameterSetter = { ps, version -> ps.setString(1, version) },
        resultMapper = { rs ->
            val rows = mutableListOf<SourceRow>()
            while (rs.next()) {
                rows.add(SourceRow(rs.getInt("id"), rs.getString("source_type"), rs.getString("created_from_filename")))
            }
            rows
        }
    )

    private val edgesQuery = DatabaseSteps.query<String, List<EdgeRow>>(
        sql = SafeSQL.select(
            """
            SELECT e.resource_source_id, e.kind, e.ref, e.count, e.expected_yield, mi.item_name
            FROM (
                SELECT resource_source_id, 'produced_item' AS kind, item AS ref, count, expected_yield, version
                FROM resource_source_produced_item WHERE version = ?
                UNION ALL
                SELECT resource_source_id, 'consumed_item', item, count, NULL, version
                FROM resource_source_consumed_item WHERE version = ?
                UNION ALL
                SELECT resource_source_id, 'produced_tag', tag, count, expected_yield, version
                FROM resource_source_produced_tag WHERE version = ?
                UNION ALL
                SELECT resource_source_id, 'consumed_tag', tag, count, NULL, version
                FROM resource_source_consumed_tag WHERE version = ?
            ) e
            LEFT JOIN minecraft_items mi ON mi.version = e.version AND mi.item_id = e.ref
            ORDER BY e.resource_source_id, e.kind, e.ref
            """.trimIndent()
        ),
        parameterSetter = { ps, version ->
            ps.setString(1, version)
            ps.setString(2, version)
            ps.setString(3, version)
            ps.setString(4, version)
        },
        resultMapper = { rs ->
            val rows = mutableListOf<EdgeRow>()
            while (rs.next()) {
                rows.add(
                    EdgeRow(
                        sourceId = rs.getInt("resource_source_id"),
                        kind = rs.getString("kind"),
                        ref = rs.getString("ref"),
                        count = rs.getInt("count"),
                        name = rs.getString("item_name"),
                        expectedYield = rs.getDouble("expected_yield").takeIf { !rs.wasNull() }
                    )
                )
            }
            rows
        }
    )

    override suspend fun process(input: String): Result<AppFailure.DatabaseError, List<ResourceSource>> {
        val sources = when (val result = sourcesQuery.process(input)) {
            is Result.Success -> result.value
            is Result.Failure -> return result
        }
        if (sources.isEmpty()) return Result.failure(AppFailure.DatabaseError.NotFound)

        val tags = when (val result = tagsQuery.process(input)) {
            is Result.Success -> result.value
            is Result.Failure -> return result
        }
        val edges = when (val result = edgesQuery.process(input)) {
            is Result.Success -> result.value
            is Result.Failure -> return result
        }

        val edgesBySource = edges.groupBy { it.sourceId }
        val resourceSources = sources.map { row ->
            val type = ResourceSource.SourceType.of(row.typeId)
            if (type == ResourceSource.SourceType.UNKNOWN) {
                logger.warn("Unknown source type '{}' for {} (version {})", row.typeId, row.filename, input)
            }
            val rowEdges = edgesBySource[row.id].orEmpty()
            ResourceSource(
                type = type,
                filename = row.filename,
                requiredItems = rowEdges.filter { it.kind.startsWith("consumed") }.map { it.toPair(tags) },
                producedItems = rowEdges.filter { it.kind.startsWith("produced") }.map { it.toPair(tags) }
            )
        }
        return Result.success(resourceSources)
    }

    private fun EdgeRow.toPair(tags: Map<String, MinecraftTag>): Pair<MinecraftId, ResourceQuantity> {
        val minecraftId: MinecraftId = if (kind.endsWith("_tag")) {
            tags[ref] ?: MinecraftTag(ref, ref.substringAfterLast(':'), emptyList())
        } else {
            Item(ref, name ?: ref.substringAfterLast(':'))
        }
        val quantity = expectedYield?.takeIf { it > 0 }
            ?.let { ResourceQuantity.ExpectedYield(it) }
            ?: ResourceQuantity.ItemQuantity(count)
        return minecraftId to quantity
    }
}
