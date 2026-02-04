package app.mcorg.domain.services

import app.mcorg.domain.model.resources.ItemSourceGraph
import app.mcorg.domain.model.resources.ResourceSource
import org.slf4j.LoggerFactory

/**
 * Service for constructing ItemSourceGraph from ResourceSource data.
 *
 * This builder processes lists of ResourceSource objects (from JSON files or database)
 * and constructs a complete bipartite graph representing all item production chains.
 *
 * Each ResourceSource becomes:
 * - A SourceNode in the graph
 * - Edges from requiredItems to the source (inputs)
 * - Edges from the source to producedItems (outputs)
 *
 * Example usage:
 * ```kotlin
 * val sources: List<ResourceSource> = loadFromJson()
 * val graph = ItemSourceGraphBuilder.buildFromResourceSources(sources)
 * ```
 */
object ItemSourceGraphBuilder {

    private val logger = LoggerFactory.getLogger(ItemSourceGraphBuilder::class.java)

    /**
     * Build an ItemSourceGraph from a collection of ResourceSource objects.
     *
     * The builder:
     * 1. Creates a SourceNode for each ResourceSource
     * 2. Creates ItemNodes for all unique items (both required and produced)
     * 3. Connects edges: requiredItems → source → producedItems
     *
     * @param sources List of ResourceSource objects from data extraction
     * @return Immutable ItemSourceGraph containing all items and sources
     */
    fun buildFromResourceSources(sources: List<ResourceSource>): ItemSourceGraph {
        logger.info("Building ItemSourceGraph from ${sources.size} resource sources")

        val builder = ItemSourceGraph.builder()
        var processedCount = 0
        var skippedCount = 0

        for (source in sources) {
            try {
                processResourceSource(builder, source)
                processedCount++
            } catch (e: Exception) {
                logger.warn("Failed to process ResourceSource: ${source.filename} (${source.type.id})", e)
                skippedCount++
            }
        }

        val graph = builder.build()
        val stats = graph.getStatistics()

        logger.info(
            "ItemSourceGraph built successfully: " +
            "${stats["itemCount"]} items, " +
            "${stats["sourceCount"]} sources, " +
            "${stats["totalEdges"]} edges " +
            "(processed: $processedCount, skipped: $skippedCount)"
        )

        return graph
    }

    /**
     * Process a single ResourceSource and add it to the graph builder.
     *
     * @param builder The graph builder to add nodes and edges to
     * @param source The ResourceSource to process
     */
    private fun processResourceSource(builder: ItemSourceGraph.Builder, source: ResourceSource) {
        // Create source node for this resource (unique by type + filename)
        val sourceNode = builder.addSourceNode(source.type, source.filename)

        // Connect required items to source (inputs)
        for (requiredItem in source.requiredItems) {
            val itemNode = builder.addItemNode(requiredItem.first.id)
            builder.addItemToSourceEdge(itemNode, sourceNode)
        }

        // Connect source to produced items (outputs)
        for (producedItem in source.producedItems) {
            val itemNode = builder.addItemNode(producedItem.first.id)
            builder.addSourceToItemEdge(sourceNode, itemNode)
        }
    }

    /**
     * Build statistics about the ResourceSource data without building the full graph.
     * Useful for validation and debugging.
     *
     * @param sources List of ResourceSource objects
     * @return Map of statistics (source count, unique items, etc.)
     */
    fun analyzeResourceSources(sources: List<ResourceSource>): Map<String, Any> {
        val uniqueItems = mutableSetOf<String>()
        val sourceTypeCount = mutableMapOf<String, Int>()
        var sourcesWithInputs = 0
        var sourcesWithOutputs = 0
        var sourcesWithBoth = 0
        var sourcesWithNeither = 0

        for (source in sources) {
            // Count items
            source.requiredItems.forEach { uniqueItems.add(it.first.id) }
            source.producedItems.forEach { uniqueItems.add(it.first.id) }

            // Count by source type
            sourceTypeCount[source.type.id] = sourceTypeCount.getOrDefault(source.type.id, 0) + 1

            // Count source patterns
            val hasInputs = source.requiredItems.isNotEmpty()
            val hasOutputs = source.producedItems.isNotEmpty()

            when {
                hasInputs && hasOutputs -> sourcesWithBoth++
                hasInputs -> sourcesWithInputs++
                hasOutputs -> sourcesWithOutputs++
                else -> sourcesWithNeither++
            }
        }

        return mapOf(
            "totalSources" to sources.size,
            "uniqueItems" to uniqueItems.size,
            "sourceTypeDistribution" to sourceTypeCount,
            "sourcesWithInputsOnly" to sourcesWithInputs,
            "sourcesWithOutputsOnly" to sourcesWithOutputs,
            "sourcesWithBoth" to sourcesWithBoth,
            "sourcesWithNeither" to sourcesWithNeither
        )
    }
}

