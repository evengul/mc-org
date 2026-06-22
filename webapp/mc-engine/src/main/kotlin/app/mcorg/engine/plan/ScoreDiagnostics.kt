package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.model.SourceNode

/**
 * Read-only introspection over the selection scorer. Given a built
 * [ItemSourceGraph], reports every candidate source for an item with its full
 * factor breakdown and the order [PlanSelector] would rank them in — the
 * data behind a "why did it pick *that*?" investigation.
 *
 * This touches no scoring weights and changes no behaviour; it is a window onto
 * [SelectionScorer]. The `selected` flag mirrors the scorer's top-ranked
 * candidate (total desc, then recipe-first, then source key) — selection's
 * structural feasibility passes can still override the top pick, so a candidate
 * marked `selected = true` here is "the scorer's favourite", not a guarantee the
 * planner committed to it.
 */
object ScoreDiagnostics {

    data class CandidateReport(
        val sourceKey: String,
        val sourceType: String,
        val method: String,
        val filename: String,
        val isRecipe: Boolean,
        val base: Int,
        val efficiency: Int,
        val supplied: Int,
        val recipeThreshold: Int,
        val selfBlockLoot: Int,
        val lowYield: Int,
        val requirementCount: Int,
        val requirementPenalty: Int,
        val chainDepth: Int,
        val depthPenalty: Int,
        val total: Int,
        val requiredItemIds: List<String>,
        val selected: Boolean
    )

    data class ItemReport(
        val itemId: String,
        val itemName: String,
        val demand: Long,
        val found: Boolean,
        val hasConstructiveSibling: Boolean,
        val candidates: List<CandidateReport>
    )

    /**
     * Builds a ranked candidate report for [itemId] at [demand]. [found] is false
     * when the graph has no item node for the id (so a `BLOCKED` item with no
     * sources at all is distinguishable from one with sources that merely score
     * poorly).
     */
    fun report(
        graph: ItemSourceGraph,
        itemId: String,
        demand: Long,
        supplied: Map<String, SupplySource> = emptyMap(),
        context: PlanContext = PlanContext()
    ): ItemReport {
        val itemNode = pickItemNode(graph, itemId)
            ?: return ItemReport(itemId, itemId.substringAfterLast(':'), demand, found = false, hasConstructiveSibling = false, candidates = emptyList())

        val item: MinecraftId = itemNode.item
        val candidates = graph.getSourcesForItem(item)
        val hasConstructiveSibling = candidates.any { it.sourceType.isConstructive() }
        val scorer = SelectionScorer(graph, supplied, context)

        // Mirror PlanSelector.rank: total desc, then recipe-first, then key.
        val ranked = candidates
            .map { source -> source to scorer.breakdown(item, source, demand, hasConstructiveSibling) }
            .sortedWith(
                compareByDescending<Pair<SourceNode, ScoreBreakdown>> { it.second.total }
                    .thenByDescending { it.first.sourceType.isRecipe() }
                    .thenBy { it.first.getKey() }
            )

        val reports = ranked.mapIndexed { index, (source, b) ->
            CandidateReport(
                sourceKey = source.getKey(),
                sourceType = source.sourceType.name,
                method = source.getMethodLabel(),
                filename = source.filename,
                isRecipe = source.sourceType.isRecipe(),
                base = b.base,
                efficiency = b.efficiency,
                supplied = b.supplied,
                recipeThreshold = b.recipeThreshold,
                selfBlockLoot = b.selfBlockLoot,
                lowYield = b.lowYield,
                requirementCount = b.requirementCount,
                requirementPenalty = b.requirementPenalty,
                chainDepth = b.chainDepth,
                depthPenalty = b.depthPenalty,
                total = b.total,
                requiredItemIds = b.requiredItemIds,
                selected = index == 0
            )
        }

        return ItemReport(
            itemId = item.id,
            itemName = item.name,
            demand = demand,
            found = true,
            hasConstructiveSibling = hasConstructiveSibling,
            candidates = reports
        )
    }

    /** Prefers the concrete item node over a same-id tag node (mirrors PlanSelector.graphItemFor). */
    private fun pickItemNode(graph: ItemSourceGraph, itemId: String) =
        graph.getItemNodesByStringId(itemId).let { nodes ->
            nodes.firstOrNull { it.item !is app.mcorg.domain.model.minecraft.MinecraftTag } ?: nodes.firstOrNull()
        }
}
