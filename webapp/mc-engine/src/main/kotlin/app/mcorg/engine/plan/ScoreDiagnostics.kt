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
        val reciprocal: Int,
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
                reciprocal = b.reciprocal,
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

    /**
     * One item whose committed source moves when a scorer behaviour is switched off.
     *
     * [with] is what ships today; [without] is what the planner commits to when the behaviour
     * is gone. Both are `PlanSelector.select` results rather than the scorer's top-ranked
     * candidate, because the selector rejects candidates structurally before scoring runs and
     * the two differ on real data — reading the ranking would credit a behaviour with
     * decisions the planner would never have emitted.
     */
    data class FactorMove(
        val itemId: String,
        val with: String,
        val withMethod: String,
        val without: String,
        val withoutMethod: String,
    )

    /**
     * What each of the four unpinned [SelectionScorer] behaviours actually decides on this
     * graph: the items whose committed source changes when it is switched off.
     *
     * This is the measurement MCO-490 needs before any constant is deleted. An empty list for
     * a factor means it is inert here and its deletion costs nothing observable. A non-empty
     * one is a list of decisions that have to be re-made — by the cost model's arithmetic if
     * it reaches the same answer, or by a ported rule if it does not.
     *
     * Read-only: every run constructs its own [PlanSelector] state and nothing is cached
     * across calls, so no ranking anywhere else is affected.
     */
    fun factorImpact(
        graph: ItemSourceGraph,
        items: List<MinecraftId>,
        demand: Long,
        context: PlanContext = PlanContext(),
    ): Map<ScorerFactor, List<FactorMove>> {
        fun committed(item: MinecraftId, mutation: ScorerMutation): SourceNode? =
            PlanSelector.select(
                graph,
                listOf(PlanTarget(item, demand)),
                context = context.copy(scorerMutation = mutation),
            ).nodes[item.id]?.source

        val shipped = items.associate { it.id to committed(it, ScorerMutation.NONE) }

        return ScorerFactor.entries.associateWith { factor ->
            val mutation = factor.mutate(context.scorerMutation)
            items.mapNotNull { item ->
                val before = shipped[item.id] ?: return@mapNotNull null
                val after = committed(item, mutation) ?: return@mapNotNull null
                if (before.getKey() == after.getKey()) null
                else FactorMove(
                    itemId = item.id,
                    with = before.getKey(),
                    withMethod = before.getMethodLabel(),
                    without = after.getKey(),
                    withoutMethod = after.getMethodLabel(),
                )
            }
        }
    }

    /**
     * True when the item's *only* producing source is breaking its own placed block —
     * "to get a stripped oak log, break a stripped oak log."
     *
     * Such items are not BLOCKED (they have a source), so they are invisible to a
     * no-sources scan, yet the acquisition the planner suggests for them is circular.
     * That makes this the working list for new synthetic sources in mc-data: each hit
     * either needs a real acquisition Mojang's JSON doesn't describe (an in-world
     * transform, a collect, an interaction), or is a legitimate self-break-only block
     * (natural stone, dirt). Exposed here rather than on the scorer so the restricted
     * scoring surface stays internal.
     */
    fun hasOnlySelfBlockLoot(graph: ItemSourceGraph, item: MinecraftId): Boolean {
        val sources = graph.getSourcesForItem(item)
        return sources.isNotEmpty() && sources.all { SelectionScorer.isSelfBlockLoot(item, it) }
    }

    /** Prefers the concrete item node over a same-id tag node (mirrors PlanSelector.graphItemFor). */
    private fun pickItemNode(graph: ItemSourceGraph, itemId: String) =
        graph.getItemNodesByStringId(itemId).let { nodes ->
            nodes.firstOrNull { it.item !is app.mcorg.domain.model.minecraft.MinecraftTag } ?: nodes.firstOrNull()
        }
}
