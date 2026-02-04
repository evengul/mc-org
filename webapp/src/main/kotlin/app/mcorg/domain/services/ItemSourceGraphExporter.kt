package app.mcorg.domain.services

import app.mcorg.domain.model.resources.ItemSourceGraph

/**
 * Utility for exporting ItemSourceGraph to various visualization formats.
 *
 * This is a simple one-time visualization tool for debugging and analysis.
 * For production visualization, consider integrating with a proper graph visualization library.
 */
object ItemSourceGraphExporter {

    /**
     * Export graph to DOT format (Graphviz).
     *
     * The output can be rendered using:
     * - Online: https://dreampuf.github.io/GraphvizOnline/
     * - CLI: `dot -Tpng graph.dot -o graph.png`
     * - CLI: `dot -Tsvg graph.dot -o graph.svg`
     *
     * @param graph The graph to export
     * @param maxItems Maximum number of items to include (for large graphs, use subset)
     * @param maxSourcesPerItem Maximum sources to show per item (prevents clutter)
     * @return DOT format string
     */
    fun toDot(
        graph: ItemSourceGraph,
        maxItems: Int = 50,
        maxSourcesPerItem: Int = 5
    ): String {
        val sb = StringBuilder()

        sb.appendLine("digraph ItemSourceGraph {")
        sb.appendLine("  rankdir=LR;")
        sb.appendLine("  node [shape=box, style=rounded];")
        sb.appendLine()

        // Node styling
        sb.appendLine("  // Item nodes (blue)")
        sb.appendLine("  node [fillcolor=lightblue, style=\"rounded,filled\"];")

        val items = graph.getAllItems().take(maxItems)
        val sources = mutableSetOf<app.mcorg.domain.model.resources.SourceNode>()

        // Add item nodes
        items.forEach { item ->
            val label = item.itemId.removePrefix("minecraft:")
            sb.appendLine("  \"${item.itemId}\" [label=\"$label\"];")

            // Collect sources for these items (limited)
            val itemSources = graph.getSourcesForItem(item.itemId).take(maxSourcesPerItem)
            sources.addAll(itemSources)
        }

        sb.appendLine()
        sb.appendLine("  // Source nodes (green)")
        sb.appendLine("  node [shape=ellipse, fillcolor=lightgreen, style=filled];")

        // Add source nodes
        sources.forEach { source ->
            val label = "${source.sourceType.name}\\n${source.filename.take(20)}"
            val sourceId = "source_${source.sourceType.id}_${source.filename}".replace(":", "_").replace(".", "_")
            sb.appendLine("  \"$sourceId\" [label=\"$label\"];")
        }

        sb.appendLine()
        sb.appendLine("  // Edges")

        // Add edges
        sources.forEach { source ->
            val sourceId = "source_${source.sourceType.id}_${source.filename}".replace(":", "_").replace(".", "_")

            // Required items -> source (red, dashed)
            graph.getRequiredItems(source).forEach { requiredItem ->
                if (items.contains(requiredItem)) {
                    sb.appendLine("  \"${requiredItem.itemId}\" -> \"$sourceId\" [color=red, style=dashed];")
                }
            }

            // Source -> produced items (green, solid)
            graph.getProducedItems(source).forEach { producedItem ->
                if (items.contains(producedItem)) {
                    sb.appendLine("  \"$sourceId\" -> \"${producedItem.itemId}\" [color=green];")
                }
            }
        }

        sb.appendLine("}")

        return sb.toString()
    }

    /**
     * Export a focused subgraph showing production chain for a specific item.
     *
     * @param graph The graph to export
     * @param targetItem The item to focus on
     * @param maxDepth Maximum depth to traverse
     * @return DOT format string showing production chain
     */
    fun toProductionChainDot(
        graph: ItemSourceGraph,
        targetItem: String,
        maxDepth: Int = 3
    ): String {
        val queries = ItemSourceGraphQueries(graph)
        val tree = queries.findProductionChain(targetItem, maxDepth) ?: return "digraph { \"Not Found\" }"

        val sb = StringBuilder()
        sb.appendLine("digraph ProductionChain {")
        sb.appendLine("  rankdir=TB;")
        sb.appendLine("  node [shape=box, style=rounded];")
        sb.appendLine()

        // Use a counter for unique source IDs
        var sourceCounter = 0
        val addedNodes = mutableSetOf<String>()

        fun addTree(tree: app.mcorg.domain.services.ProductionTree, indent: String = "  ") {
            val itemId = tree.targetItem.itemId
            val itemLabel = itemId.removePrefix("minecraft:")

            if (itemId !in addedNodes) {
                sb.appendLine("$indent\"$itemId\" [label=\"$itemLabel\", fillcolor=lightblue, style=\"rounded,filled\"];")
                addedNodes.add(itemId)
            }

            tree.sources.forEach { branch ->
                val sourceId = "source_${sourceCounter++}"
                val sourceLabel = "${branch.source.sourceType.name}\\n${branch.source.filename.take(15)}"

                sb.appendLine("$indent\"$sourceId\" [label=\"$sourceLabel\", shape=ellipse, fillcolor=lightgreen, style=filled];")
                sb.appendLine("$indent\"$sourceId\" -> \"$itemId\" [color=green];")

                branch.requiredItems.forEach { requiredTree ->
                    addTree(requiredTree, indent)
                    sb.appendLine("$indent\"${requiredTree.targetItem.itemId}\" -> \"$sourceId\" [color=red, style=dashed];")
                }
            }
        }

        addTree(tree)

        sb.appendLine("}")
        return sb.toString()
    }

    /**
     * Export graph to simple text format for quick viewing.
     *
     * @param graph The graph to export
     * @param maxItems Maximum items to show
     * @return Text representation
     */
    fun toText(graph: ItemSourceGraph, maxItems: Int = 20): String {
        val sb = StringBuilder()
        val stats = graph.getStatistics()

        sb.appendLine("=".repeat(60))
        sb.appendLine("ITEM SOURCE GRAPH SUMMARY")
        sb.appendLine("=".repeat(60))
        sb.appendLine()
        sb.appendLine("Statistics:")
        stats.forEach { (key, value) ->
            sb.appendLine("  $key: $value")
        }
        sb.appendLine()
        sb.appendLine("-".repeat(60))
        sb.appendLine("ITEMS (showing first $maxItems)")
        sb.appendLine("-".repeat(60))

        graph.getAllItems().take(maxItems).forEach { item ->
            sb.appendLine("\n${item.itemId}:")
            val sources = graph.getSourcesForItem(item.itemId)

            if (sources.isEmpty()) {
                sb.appendLine("  [No sources - unreachable item]")
            } else {
                sources.take(5).forEach { source ->
                    sb.appendLine("  └─ ${source.sourceType.name} (${source.filename})")
                    val required = graph.getRequiredItems(source)
                    if (required.isEmpty()) {
                        sb.appendLine("     └─ [No requirements - base resource]")
                    } else {
                        required.take(3).forEach { req ->
                            sb.appendLine("     └─ requires: ${req.itemId}")
                        }
                        if (required.size > 3) {
                            sb.appendLine("     └─ ... and ${required.size - 3} more")
                        }
                    }
                }
                if (sources.size > 5) {
                    sb.appendLine("  └─ ... and ${sources.size - 5} more sources")
                }
            }
        }

        return sb.toString()
    }
}

