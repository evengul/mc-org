package app.mcorg.pipeline.minecraft

import app.mcorg.config.CacheManager
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

/**
 * Runtime seam between the persisted Minecraft data and the pure planning
 * engine: loads a version's `resource_source_*` rows back into [ResourceSource]s
 * and builds the [ItemSourceGraph] the planner runs on, cached per version.
 * Versions are immutable, so a cached graph only ever changes on re-ingest
 * (which calls [CacheManager.onVersionIngested]).
 */
object GetItemSourceGraphForVersionStep : Step<String, AppFailure, ItemSourceGraph> {

    override suspend fun process(input: String): Result<AppFailure, ItemSourceGraph> {
        CacheManager.itemSourceGraph.getIfPresent(input)?.let { return Result.success(it) }
        return LoadResourceSourcesForVersionStep.process(input).map { sources ->
            ItemSourceGraphBuilder.buildFromResourceSources(sources)
                .also { CacheManager.itemSourceGraph.put(input, it) }
        }
    }
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
